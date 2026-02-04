/*
 * Copyright 2026 dorkbox, llc
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


import dorkbox.gradle.StaticMethodsAndTools
import dorkbox.gradlePublish.JarFixup.fixupJar
import dorkbox.gradlePublish.JavaDocFixup.fixJavadocJar
import dorkbox.gradlePublish.LicenseFilesFixup.fixupLicenseFiles
import dorkbox.gradlePublish.MavenFixup.fixMaven
import dorkbox.gradlePublish.SigningFixup.fixSigning
import dorkbox.gradlePublish.SigningFixup.setupInMemoryKeys
import dorkbox.gradlePublish.SourcesJarFixup.fixSourcesJar
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningPlugin
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter



/**
 * For managing (what should be common sense) gradle tasks, such as:
 *  - publishing gradle projects to sonatype
 */
@Suppress("unused")
class PublishPlugin : Plugin<Project> {
    companion object {
        const val PUBLICATION_NAME = "MavenCentral"
        const val PUBLICATION_NAME_LOWER = "maven-central"

        val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("E MMM HH:mm:ss 'UTC' yyyy").withZone(ZoneOffset.UTC)

        @Suppress("UNCHECKED_CAST", "SameParameterValue")
        fun Project.assignFromProp(propertyName: String, defaultValue: String, apply: (value: String)->Unit) {
            // THREE possibilities for property registration or assignment
            // 1) we have MANUALLY defined this property (via the configuration object)
            // 2) gradleUtil properties loaded first
            //      -> gradleUtil's adds a function that everyone else (plugin/task) can call to get values from properties
            // 3) gradleUtil properties loaded last
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
            val loaderFunctions: java.util.ArrayList<Plugin<Pair<String, String>>>?
            if (project.extensions.extraProperties.has("property_loader_functions")) {
                loaderFunctions = project.extensions.extraProperties["property_loader_functions"] as java.util.ArrayList<Plugin<Pair<String, String>>>?
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

    override fun apply(project: Project) {

        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        project.apply("java")
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)


        // make sure the publish project is also applied to all children projects...
        project.childProjects.values.forEach {
            it.pluginManager.apply(PublishPlugin::class.java)
        }

        // Create the Plugin extension object (for users to configure publishing).
        val config = project.extensions.create("mavenCentral", PublishToMavenCentral::class.java, project)

        // setup the sonatype PRIVATE KEY information
        project.assignFromProp("sonatypePrivateKeyFile", "") { fileName ->
            project.extensions.extraProperties["sonatypePrivateKeyFile"] = fileName
            project.setupInMemoryKeys()
        }

        project.assignFromProp("sonatypePrivateKeyPassword", "") { password ->
            project.extensions.extraProperties["sonatypePrivateKeyPassword"] = password
            project.setupInMemoryKeys()
        }

        project.assignFromProp("mavenCentralPublishUsername", config.sonatype.userName) {
            project.extensions.extraProperties["mavenCentralPublishUsername"] = it
        }
        project.assignFromProp("mavenCentralPublishPassword", config.sonatype.password) {
            project.extensions.extraProperties["mavenCentralPublishPassword"] = it
        }


        project.pluginManager.withPlugin("java") { _ ->
            project.configure<JavaPluginExtension> {
                withJavadocJar()
                runCatching {
                    withSourcesJar()
                }.onFailure { e ->
                    project.logger.warn(
                        "Could not configure the Java extension's sourcesJar task, received {}: {}",
                        e::class.simpleName,
                        e.message,
                    )
                }
            }

           /*
            * Copyright 2025  Danilo Pianini
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
            *
            * https://www.danilopianini.org
            * https://github.com/DanySK/publish-on-central
            */
            project.configure<PublishingExtension> {
                publications { publications ->
                    if (publications.none { it.name == PUBLICATION_NAME }) {
                        publications.register(PUBLICATION_NAME, MavenPublication::class.java) { publication ->
                            val componentProvider =
                                project.components.withType<DefaultJvmSoftwareComponent>().named("java") { javaComponent ->
                                    javaComponent.withJavadocJar()
                                    javaComponent.withSourcesJar()
                                }
                            publication.from(componentProvider.get())
                            publication.pom.packaging = "jar"
                        }
                        project.logger.debug("Created new publication $PUBLICATION_NAME")
                    }
                }
            }
        }

        // we have to generate the POM file BEFORE the jar (so the outputs are available!)
        project.tasks.withType<GenerateMavenPom>().configureEach { pomTask ->
            pomTask.mustRunAfter(project.tasks.withType<Jar>())

            val pomFile = pomTask.outputs.files.first()
            val pomProps = pomFile.parentFile.resolve("pom.properties")

            pomTask.doFirst {
                // have to write out the pom.properties file
                pomProps.writeText(
                    "# Generated by Dorkbox\n" +
                    "# ${DTF.format(Instant.now())}\n" +
                    "version=${config.version}\n" +
                    "groupId=${config.groupId}\n" +
                    "artifactId=${config.artifactId}\n")
            }
        }

        // Defer logic that depends on project state
        project.afterEvaluate {
            if (!config.configured) {
                project.plugins.findPlugin("com.dorkbox.GradleUtils")?.let { plugin ->
                    val data = project.extensions.getByType(StaticMethodsAndTools::class.java).data

                    // automatically configure mavenCentral
                    config.apply {
                        println("\tAutomatically configuring MavenCentral plugin")
                        groupId = data.group
                        artifactId = data.id
                        version = data.version

                        name = data.name
                        description = data.description
                        url = data.url

                        vendor = data.vendor
                        vendorUrl = data.vendorUrl

                        issueManagement {
                            url = data.issueManagement.url
                            nickname = data.issueManagement.nickname
                        }

                        developer {
                            id = data.developer.id
                            name = data.developer.name
                            email = data.developer.email
                        }
                    }
                }
            }
        }

        val repoToConfigure = Repository.projectLocalRepository(project)


        project.fixupLicenseFiles()
        project.fixupJar()
        project.fixSourcesJar()
        project.fixSigning(repoToConfigure)
        project.fixJavadocJar(config)
        project.fixMaven(config, repoToConfigure)
    }

    // required to make sure the plugins are correctly applied. ONLY applying it to the project WILL NOT work.
    // The plugin must also be applied to the root project
    private fun Project.apply(id: String) {
        if (rootProject.pluginManager.findPlugin(id) == null) {
            rootProject.pluginManager.apply(id)
        }

        if (pluginManager.findPlugin(id) == null) {
            pluginManager.apply(id)
        }
    }

    private inline fun <reified S : Any> DomainObjectCollection<in S>.withType(noinline configuration: S.() -> Unit) =
            withType(S::class.java, configuration)
}
