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

import dorkbox.gradlePublish.ProjectExtensions.warnIfCredentialsAreMissing
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.signatory.internal.pgp.InMemoryPgpSignatoryProvider
import java.io.File


object SigningFixup {
    fun Project.setupInMemoryKeys() {
        val sign = extensions.getByName("signing") as SigningExtension

        // check what the signatory is. if it's InMemoryPgpSignatoryProvider, then we ALREADY configured it!
        if (sign.signatory !is InMemoryPgpSignatoryProvider) {
            // we haven't configured it yet AND we don't know which value is set first!

            // setup the sonatype PRIVATE KEY information
            if (project.extensions.extraProperties.has("sonatypePrivateKeyPassword") && project.extensions.extraProperties.has("sonatypePrivateKeyFile")) {
                val password = project.extensions.extraProperties["sonatypePrivateKeyPassword"] as String
                val fileText = File(project.extensions.extraProperties["sonatypePrivateKeyFile"] as String).readText()
                if (fileText.isNotEmpty()) {
                    sign.apply {
                        useInMemoryPgpKeys(fileText, password)
                    }
                }
            }
        }
    }

    fun Project.fixSigning(repoToConfigure: Repository) {
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
        extensions.configure(PublishingExtension::class) { publishing ->
            publishing.repositories { repository ->
                repository.maven { mavenArtifactRepository ->
                    mavenArtifactRepository.name = repoToConfigure.name
                    mavenArtifactRepository.url = repoToConfigure.url.get()

                    if (mavenArtifactRepository.url.scheme != "file") {
                        mavenArtifactRepository.credentials { credentials ->
                            credentials.username = repoToConfigure.user.orNull
                            credentials.password = repoToConfigure.password.orNull
                        }
                    }
                    tasks.withType(PublishToMavenRepository::class) {
                        if (it.repository == mavenArtifactRepository) {
                            it.doFirst {
                                warnIfCredentialsAreMissing(repoToConfigure)
                            }
                        }
                    }
                }
            }
        }

        tasks.withType<Sign>() {
            group = PublishingPlugin.PUBLISH_TASK_GROUP
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
        configure<PublishingExtension> {
            publications
                .withType<MavenPublication>()
                .all { publication ->
                    configure<SigningExtension> {
                        sign(publication)
                    }
                }
        }

        tasks.withType<PublishToMavenRepository>().configureEach { publish ->
            publish.mustRunAfter(tasks.withType<Sign>())
        }
    }
}
