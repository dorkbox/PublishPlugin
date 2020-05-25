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
import dorkbox.gradle.sourceSets
import io.codearte.gradle.nexus.NexusStagingExtension
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar


/**
 * For managing (what should be common sense) gradle tasks, such as:
 *  - publishing gradle projects to sonatype
 */
@Suppress("UnstableApiUsage", "unused")
class PublishPlugin : Plugin<Project> {
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

            repositories(Action<NexusRepositoryContainer> {
                it.sonatype(Action<NexusRepository> { repo ->
                    repo.username.set(project.provider { config.sonatype.userName })
                    repo.password.set(project.provider { config.sonatype.password })
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
                    }
                    pom.scm {
                    }
                    pom.developers {
                    }
                }

                mavPub.artifact(project.tasks.create("sourceJar", Jar::class.java).apply {
                    description = "Creates a JAR that contains the source code."

                    from(project.sourceSets["main"]?.java)
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
            group = "publishing"

            dependsOn("publishToSonatype", "closeAndReleaseRepository")
        }


        project.tasks.withType<PublishToMavenLocal> {
            onlyIf {
                val pub = get()
                publication == pub.publications.getByName("maven")
            }
        }

        project.tasks.withType<PublishToMavenRepository> {
             doFirst {
                 logger.debug("Publishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to ${repository.url}")
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

                println("Maven URL: $url$projectName/${config.name}/${config.version}/")
            }

            nexusStaging {
                username = config.sonatype.userName
                password = config.sonatype.password
            }
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
}
