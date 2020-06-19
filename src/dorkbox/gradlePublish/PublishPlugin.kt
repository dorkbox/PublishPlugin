/*
 * Copyright 2020 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.gradlePublish

import de.marcphilipp.gradle.nexus.NexusPublishExtension
import de.marcphilipp.gradle.nexus.NexusRepository
import de.marcphilipp.gradle.nexus.NexusRepositoryContainer
import io.codearte.gradle.nexus.CloseRepositoryTask
import io.codearte.gradle.nexus.NexusStagingExtension
import io.codearte.gradle.nexus.ReleaseRepositoryTask
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.signatory.internal.pgp.InMemoryPgpSignatoryProvider
import java.io.File
import java.time.Duration
import java.util.*
import java.util.function.BiFunction


/**
 * For managing (what should be common sense) gradle tasks, such as:
 *  - publishing gradle projects to sonatype
 */
@Suppress("UnstableApiUsage", "unused")
class PublishPlugin : Plugin<Project> {
    companion object {
        init {
            // To fix maven+gradle moronic incompatibilities: https://github.com/gradle/gradle/issues/11308
            System.setProperty("org.gradle.internal.publish.checksums.insecure", "true")
        }
    }

    private lateinit var project: Project

    override fun apply(project: Project) {
        this.project = project

        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        apply("java")
        apply("maven-publish")
        apply("signing")
        apply("de.marcphilipp.nexus-publish")
        apply("io.codearte.nexus-staging")

        // Create the Plugin extension object (for users to configure publishing).
        val config = project.extensions.create("publishToSonatype", PublishToSonatype::class.java, project)

        // specific configuration later in after evaluate!!
        nexusPublishing {
            packageGroup.set(project.provider {config.groupId })

            clientTimeout.set(project.provider { config.httpTimeout })
            connectTimeout.set(project.provider { config.httpTimeout })

            repositories(Action<NexusRepositoryContainer> {
                it.sonatype(Action<NexusRepository> { repo ->
                    assignFromProp("sonatypeUserName", config.sonatype.userName) { repo.username.set(project.provider { it }) }
                    assignFromProp("sonatypePassword", config.sonatype.password) { repo.password.set(project.provider { it }) }
                })
            })
        }

        publishing {
            publications { pub ->
                val mavPub = pub.maybeCreate("maven", MavenPublication::class.java)

                mavPub.from(project.components.getByName("java"))
                // create the pom
                mavPub.pom { pom ->
                    pom.organization {
                    }
                    pom.issueManagement {
                        val sign = project.extensions.getByName("signing") as SigningExtension

                        // check what the signatory is. if it's InMemoryPgpSignatoryProvider, then we ALREADY configured it!
                        if (sign.signatory !is InMemoryPgpSignatoryProvider) {
                            // we haven't configured it yet AND we don't know which value is set first!

                            // setup the sonatype PRIVATE KEY information
                            assignFromProp("sonatypePrivateKeyFile", "") {
                                project.extensions.extraProperties["sonatypePrivateKeyFile"] = it

                                if (project.extensions.extraProperties.has("sonatypePrivateKeyPassword")) {
                                    sign.apply {
                                        useInMemoryPgpKeys(File(it).readText(), project.extensions.extraProperties["sonatypePrivateKeyPassword"] as String)
                                    }
                                }
                            }

                            assignFromProp("sonatypePrivateKeyPassword", "") {
                                project.extensions.extraProperties["sonatypePrivateKeyPassword"] = it

                                if (project.extensions.extraProperties.has("sonatypePrivateKeyFile")) {
                                    sign.apply {
                                        useInMemoryPgpKeys(File(project.extensions.extraProperties["sonatypePrivateKeyFile"] as String).readText(), it)
                                    }
                                }
                            }
                        }
                    }
                    pom.scm {
                    }
                    pom.developers {
                    }
                }

                mavPub.artifact(project.tasks.create("sourceJar", Jar::class.java).apply {
                    description = "Creates a JAR that contains the source code."

                    val sourceSets = project.extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer
                    from(sourceSets.getByName("main").java)
                    archiveClassifier.set("sources")
                })

                mavPub.artifact(project.tasks.create("javaDocJar", Jar::class.java).apply {
                    description = "Creates a JAR that contains the javadocs."
                    archiveClassifier.set("javadoc")
                })
            }
        }

        project.tasks.getByName("closeAndReleaseRepository").mustRunAfter(project.tasks.getByName("publishToSonatype"))
        project.tasks.create("publishToSonatypeAndRelease", PublishAndReleaseProjectTask::class.java).apply {
            group = "publish and release"

            dependsOn("publishToSonatype", "closeAndReleaseRepository")
        }

        project.tasks.getByName("publishToMavenLocal").apply {
            group = "publish and release"
        }


        project.tasks.withType<PublishToMavenLocal> {
            onlyIf {
                val pub = get()
                publication == pub.publications.getByName("maven")
            }
        }

        project.tasks.withType<PublishToMavenRepository> {
             doFirst {
                 println("\tPublishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to ${repository.url}")
             }

            onlyIf {
                val pub = get()
                publication == pub.publications.getByName("maven") &&
                repository == pub.repositories.getByName("sonatype")
            }
        }


        // have to get the configuration extension data
        // required to make sure the tasks run in the correct order
        project.afterEvaluate {
            // output the release URL in the console
            project.tasks.getByName("releaseRepository").doLast {
                val url = "https://oss.sonatype.org/content/repositories/releases/"
                val projectName = config.groupId.replace('.', '/')

                println("\tMaven URL: $url$projectName/${config.name}/${config.version}/")
            }

            nexusStaging {
                assignFromProp("sonatypeUserName", config.sonatype.userName) { username = it }
                assignFromProp("sonatypePassword", config.sonatype.password) { password = it }
            }

            project.tasks.findByName("publishToSonatype")?.doFirst {
                println("\tPublishing to Sonatype: ${config.groupId}:${config.version}")
            }

            val closeTask = project.tasks.findByName("closeRepository") as CloseRepositoryTask
            closeTask.apply {
                delayBetweenRetriesInMillis = config.retryDelay.toMillis().toInt()
                numberOfRetries = config.retryLimit
            }

            val releaseTask = project.tasks.findByName("releaseRepository") as ReleaseRepositoryTask
            releaseTask.apply {
                delayBetweenRetriesInMillis = config.retryDelay.toMillis().toInt()
                numberOfRetries = config.retryLimit
            }

            // create the sign task to sign the artifact jars before uploading
            val sign = project.extensions.getByName("signing") as SigningExtension
            sign.apply {
                sign((project.extensions.getByName("publishing") as PublishingExtension).publications.getByName("maven"))
            }


            // output how much the time-outs are
            val durationString = config.httpTimeout.toString().substring(2)
                    .replace("(\\d[HMS])(?!$)", "$1 ").toLowerCase()


            val fullReleaseTimeout = Duration.ofMillis(config.retryDelay.toMillis() * config.retryLimit)
            val fullReleaseString = fullReleaseTimeout.toString().substring(2)
                    .replace("(\\d[HMS])(?!$)", "$1 ").toLowerCase()

            println("\tMaven publish and release plugin initialized")
            println("\t- Sonatype HTTP timeout: $durationString")
            println("\t- Sonatype API timeout: $fullReleaseString")
        }

        project.childProjects.values.forEach {
            it.pluginManager.apply(PublishPlugin::class.java)
        }
    }

    // required to make sure the plugins are correctly applied. ONLY applying it to the project WILL NOT work.
    // The plugin must also be applied to the root project
    private fun apply(id: String) {
        if (project.rootProject.pluginManager.findPlugin(id) == null) {
            project.rootProject.pluginManager.apply(id)
        }

        if (project.pluginManager.findPlugin(id) == null) {
            project.pluginManager.apply(id)
        }
    }

    private inline fun <reified S : Any> DomainObjectCollection<in S>.withType(noinline configuration: S.() -> Unit) =
            withType(S::class.java, configuration)

    @Suppress("UNCHECKED_CAST")
    private fun get() : PublishingExtension {
        return project.extensions.getByName("publishing") as PublishingExtension
    }

    private fun publishing(configure: PublishingExtension.() -> Unit): Unit =
            project.extensions.configure("publishing", configure)

    private fun nexusStaging(configure: NexusStagingExtension.() -> Unit): Unit =
            project.extensions.configure("nexusStaging", configure)

    private fun nexusPublishing(configure: NexusPublishExtension.() -> Unit): Unit =
            project.extensions.configure("nexusPublishing", configure)


    @Suppress("UNCHECKED_CAST")
    private fun assignFromProp(propertyName: String, defaultValue: String, apply: (value: String)->Unit) {
        // THREE possibilities for property registration or assignment
        // 1) we have MANUALLY defined this property (via the configuration object)
        // 1) gradleUtil properties loaded first
        //      -> gradleUtil's adds a function that everyone else (plugin/task) can call to get values from properties
        // 2) gradleUtil properties loaded last
        //      -> others add a function that gradleUtil's call to set values from properties


        // 1
        if (defaultValue.isNotEmpty()) {
//            println("ASSIGN DEFAULT: $defaultValue")
            apply(defaultValue)
            return
        }

        // 2
        if (project.extensions.extraProperties.has(propertyName)) {
//                println("ASSIGN PROP FROM FILE: $propertyName")
            apply(project.extensions.extraProperties[propertyName] as String)
            return
        }

        // 3
        var loaderFunctions: ArrayList<Plugin<Pair<String, String>>>? = null
        if (project.extensions.extraProperties.has("property_loader_functions")) {
            loaderFunctions = project.extensions.extraProperties["property_loader_functions"] as ArrayList<Plugin<Pair<String,String>>>?
        } else {
            loaderFunctions = ArrayList<Plugin<Pair<String, String>>>()
            project.extensions.extraProperties["property_loader_functions"] = loaderFunctions
        }

//            println("ADD LOADER FUNCTION: $propertyName")
        loaderFunctions!!.add(Plugin<Pair<String, String>>() {
            if (it.first == propertyName) {
//                    println("EXECUTE LOADER FUNCTION: $propertyName")
                apply(it.second)
            }
        })
    }
}
