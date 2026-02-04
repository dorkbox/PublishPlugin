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

import dorkbox.gradlePublish.PublishPlugin.Companion.PUBLICATION_NAME_LOWER
import dorkbox.gradlePublish.portal.PublishPortalDeployment
import dorkbox.gradlePublish.portal.PublishPortalDeployment.Companion.DROP_TASK_NAME
import dorkbox.gradlePublish.portal.PublishPortalDeployment.Companion.RELEASE_TASK_NAME
import dorkbox.gradlePublish.portal.PublishPortalDeployment.Companion.VALIDATE_TASK_NAME
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin.PUBLISH_LOCAL_LIFECYCLE_TASK_NAME
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import java.io.File

object MavenFixup {
    private const val PUBLISH_GROUP = "publish and release"

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
    private inline fun <reified T : Any> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
        objects.property<T>().apply { convention(provider(default)) }


    fun Project.fixMaven(config: PublishToMavenCentral, repoToConfigure: Repository) {
        manageMavenCentralPortal(this, repoToConfigure)

        tasks.register("publishToMavenAndRelease") { task ->
            task.outputs.upToDateWhen { false }
            task.outputs.cacheIf { false }

            task.group = PUBLISH_GROUP
            task.description = "Publish and Release this project to the Sonatype Maven repository"

            task.dependsOn("clean", PUBLISH_LOCAL_LIFECYCLE_TASK_NAME, VALIDATE_TASK_NAME, RELEASE_TASK_NAME)
        }

        tasks.named(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME) { task ->
            task.dependsOn("clean")
            task.group = PUBLISH_GROUP
        }

        tasks.register("getMavenUrl") { task ->
            task.outputs.upToDateWhen { false }
            task.outputs.cacheIf { false }

            task.group = PUBLISH_GROUP

            task.doLast {
                val url = "https://repo1.maven.org/maven2"
                val projectName = config.groupId.replace('.', '/')

                if (projectName.isNotBlank()) {
                    // output the release URL in the console
                    println("\tMaven Central URL: $url/$projectName/${config.name}/${config.version}/")
                } else {
                    println("\tCannot display Maven Central URL, project details are not configured.")
                }
            }
        }


        tasks.register("getSonatypeUrl") { task ->
            task.outputs.upToDateWhen { false }
            task.outputs.cacheIf { false }

            task.group = PUBLISH_GROUP

            task.doLast {
                val url = "https://oss.sonatype.org/content/repositories/releases/"
                val projectName = config.groupId.replace('.', '/')

                if (projectName.isNotBlank()) {
                    // output the release URL in the console
                    println("\tSonatype URL: $url$projectName/${config.name}/${config.version}/")
                } else {
                    println("\tCannot display Sonatype URL, project details are not configured.")
                }
            }
        }

        tasks.withType<PublishToMavenLocal> {
            doFirst {
                // prune off the "file:"
                val localMavenRepo = repositories.mavenLocal().url.toString().replaceFirst("file:", "")

                val projectName = config.groupId.replace('.', '/')

                // output the release URL in the console

                val mavenLocation = "$localMavenRepo$projectName/${config.name}/${config.version}/"

                // clean-out the repo!!
                File(mavenLocation).deleteRecursively()

                println("\tPublishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to Maven Local")

                publication.artifacts.forEach {
                    val file = File("$mavenLocation/${it.file.name}")
                    println("\t\t$file")
                }
            }
        }

        tasks.withType<PublishToMavenRepository> {
            doFirst {
                val projectName = config.groupId.replace('.', '/')

                // prune off the "file:"
                if (repository.url.toURL().protocol == "file") {
                    val localMavenRepo = repository.url.toString().replaceFirst("file:", "")
                    println("\tPublishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to ${localMavenRepo}$projectName/${config.name}/${config.version}/")
                } else {
                    println("\tPublishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to ${repository.url}$projectName/${config.name}/${config.version}/")
                }
            }
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
    private fun manageMavenCentralPortal(project: Project, repoToConfigure: Repository) {
        // Maven Central Portal configuration
        val zipMavenCentralPortal =
            project.tasks.register<Zip>("zipMavenCentralPortalPublication") {
                group = PublishingPlugin.PUBLISH_TASK_GROUP
                description = "Creates a zip file containing the project-local Maven repository"

                // NOTE: there cannot be ANY files in the root dir! They must ONLY be in the bundle dir!

                exclude("LICENSE", "LICENSE.blob")

                // https://slack-chats.kotlinlang.org/t/16407246/anyone-tried-the-https-central-sonatype-org-publish-publish-
                // it doesn't like maven-metadata files!
                exclude("**/maven-metadata.*")

                from(repoToConfigure.url)
                archiveBaseName.set(project.name)

                destinationDirectory.set(project.layout.buildDirectory.dir(PUBLICATION_NAME_LOWER))
                dependsOn("clean")
                dependsOn(project.tasks.withType<PublishToMavenRepository>())
                dependsOn(project.tasks.withType<PublishToMavenLocal>())
            }


        val portalDeployment = PublishPortalDeployment(
            project = project,
            baseUrl = "https://central.sonatype.com/",
            user = project.propertyWithDefaultProvider {
                System.getenv("MAVEN_CENTRAL_PORTAL_USERNAME") ?: System.getenv("MAVEN_CENTRAL_USERNAME")
                ?: project.extensions.extraProperties["mavenCentralPublishUsername"]?.toString()
                ?: project.properties["mavenCentralPortalUsername"]?.toString() ?: project.properties["centralPortalUsername"]?.toString()
                ?: project.properties["centralUsername"]?.toString()
            },
            password = project.propertyWithDefaultProvider {
                System.getenv("MAVEN_CENTRAL_PORTAL_PASSWORD") ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
                ?: project.extensions.extraProperties["mavenCentralPublishPassword"]?.toString()
                ?: project.properties["mavenCentralPortalPassword"]?.toString() ?: project.properties["centralPortalPassword"]?.toString()
                ?: project.properties["centralPassword"]?.toString()
            },
            zipTask = zipMavenCentralPortal,
        )

        project.tasks.register("saveMavenCentralPortalDeploymentId") { save ->
            val fileName = "maven-central-portal-bundle-id"
            val file = project.rootProject.layout.buildDirectory.map { it.asFile.resolve(fileName) }

            save.group = PublishingPlugin.PUBLISH_TASK_GROUP
            save.description = "Saves the Maven Central Portal deployment ID locally in ${file.get().absolutePath}"

            save.dependsOn(zipMavenCentralPortal)
            save.outputs.file(file)

            save.doLast {
                file.get().writeText("${portalDeployment.fileToUpload}=${portalDeployment.deploymentId}\n")
            }
        }

        val validate = project.tasks.register(VALIDATE_TASK_NAME) { validate ->
            validate.group = PublishingPlugin.PUBLISH_TASK_GROUP
            validate.description = "Validates the Maven Central Portal publication, uploading if needed"

            validate.dependsOn(zipMavenCentralPortal)
            validate.doLast {
                runBlocking {
                    portalDeployment.validate()
                }
            }
        }


        project.tasks.register(DROP_TASK_NAME) { drop ->
            drop.group = PublishingPlugin.PUBLISH_TASK_GROUP
            drop.description = "Drops the Maven Central Portal publication"

            drop.mustRunAfter(validate)
            drop.mustRunAfter(zipMavenCentralPortal)
            drop.doLast {
                runBlocking {
                    portalDeployment.drop()
                }
            }
        }


        project.tasks.register(RELEASE_TASK_NAME) { release ->
            release.group = PublishingPlugin.PUBLISH_TASK_GROUP
            release.description = "Releases the Maven Central Portal publication"

            release.mustRunAfter(validate)
            release.mustRunAfter(zipMavenCentralPortal)
            release.doLast {
                runBlocking {
                    portalDeployment.release()
                }
            }
        }

        project.gradle.taskGraph.whenReady { taskGraph ->
            val allTasks = taskGraph.allTasks.map { it.name }.toSet()
            check(RELEASE_TASK_NAME !in allTasks || DROP_TASK_NAME !in allTasks) {
                "Task $RELEASE_TASK_NAME and $DROP_TASK_NAME cannot be executed together"
            }
        }
    }
}
