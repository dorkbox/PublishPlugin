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
import org.gradle.api.*
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import java.io.File

open class
PublishAndReleaseProjectTask : DefaultTask() {
//    private lateinit var mavenPublication: MavenPublication

    init {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
        description = "Publish and Release this project to the Sonatype Maven repository"
    }

    @TaskAction
    fun run() {
        // have to get the configuration extension data
//        val config = project.extensions.getByName("publishToSonatype") as PublishToSonatype
//
//        if (!config.isValid()) {
//            throw GradleException("publish configuration data is incomplete.")
//        }

        println("Publishing and releasing project to sonatype!")

        ///////////////////////////////
        //////    PUBLISH TO SONATYPE / MAVEN CENTRAL
        //////
        ////// TESTING (local maven repo) -> "PUBLISHING" -> publishToMavenLocal
        //////
        ////// RELEASE (sonatype / maven central) -> "PUBLISHING" -> publishToSonaytypeAndRelease
        ///////////////////////////////
//        publishing {
//            publications { pub ->
//                pub.create("maven", MavenPublication::class.java) { mavPub ->
//                    mavPub.groupId = config.groupId
//                    mavPub.artifactId = config.artifactId
//                    mavPub.version = config.version
//
//                    mavPub.pom { pom ->
//                        pom.name.set(config.name)
//                        pom.description.set(config.description)
//                        pom.url.set(config.url)
//
//
//                        pom.issueManagement {
//                            it.url.set("${config.issueManagement.url}/issues")
//                            it.system.set(config.issueManagement.nickname)
//                        }
//
//                        pom.organization {
//                            it.name.set(config.vendor)
//                            it.url.set(config.vendorUrl)
//                        }
//
//                        pom.developers {
//                            it.developer { dev ->
//                                dev.id.set(config.developer.id)
//                                dev.name.set(config.developer.name)
//                                dev.email.set(config.developer.email)
//                            }
//                        }
//
//                        pom.scm {
//                            it.url.set(config.url)
//                            it.connection.set("scm:${config.url}.git")
//                        }
//                    }
//                }
//            }
//        }

//        // output the release URL in the console
//        project.tasks.getByName("releaseRepository").doLast {
//            val url = "https://oss.sonatype.org/content/repositories/releases/"
//            val projectName = config.groupId.replace('.', '/')
//
//            println("Maven URL: $url$projectName/${config.name}/${config.version}/")
//        }
//
//        project.nexusStaging {
//            username = config.sonatype.userName
//            password = config.sonatype.password
//        }
//
//        val pub = (project as org.gradle.api.plugins.ExtensionAware).extensions.getByName("publishing") as NexusPublishExtension
//        pub.apply {
//            packageGroup.set(config.groupId)
//
//            repositories(Action<NexusRepositoryContainer> {
//                it.sonatype(Action<NexusRepository> {repo ->
//                    repo.username.set(config.sonatype.userName)
//                    repo.password.set(config.sonatype.password)
//                })
//            })
//        }
//
//        project.signing {
//            useInMemoryPgpKeys(File(config.privateKey.fileName).readText(), config.privateKey.password)
//            sign(project.publishing.publications.getByName("maven"))
//        }
    }

//    private val Project.publishing: org.gradle.api.publish.PublishingExtension get() =
//        (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("publishing") as org.gradle.api.publish.PublishingExtension

//    private fun Project.publishing(configure: org.gradle.api.publish.PublishingExtension.() -> Unit): Unit =
//            (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("publishing", configure)

//    private inline fun <reified S : Any> DomainObjectCollection<in S>.withType(noinline configuration: S.() -> Unit) =
//            withType(S::class.java, configuration)
//
//    private fun Project.nexusStaging(configure: io.codearte.gradle.nexus.NexusStagingExtension.() -> Unit): Unit =
//            (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("nexusStaging", configure)
//
//    private fun Project.nexusPublishing(configure: NexusPublishExtension.() -> Unit): Unit =
//            (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("nexusPublishing", configure)
//
//    private fun Project.signing(configure: org.gradle.plugins.signing.SigningExtension.() -> Unit): Unit =
//            (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("signing", configure)
}
