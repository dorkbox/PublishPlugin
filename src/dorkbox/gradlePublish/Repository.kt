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

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property
import java.net.URI
import java.time.Duration

/**
 * A class modeling the concept of target Maven repository.
 * Includes a [name], an [url], and methods to compute [user] and [password] given a [Project].
 * If the repository is managed with Sonatype Nexus,
 * then the Nexus uri should be provided as [nexusUrl].
 * Timeouts can be set with [nexusTimeOut] and [nexusConnectTimeOut].
 */
data class Repository(
    var name: String,
    val url: Provider<URI>,
    val user: Property<String>,
    val password: Property<String>,
    val nexusUrl: String? = null,
    val nexusTimeOut: Duration = Duration.ofMinutes(1),
    val nexusConnectTimeOut: Duration = Duration.ofMinutes(1),
) {
    override fun toString() = "$name at ${url.orNull}"

    /**
     * Constants and utility functions.
     */
    companion object {
        /**
         * Creates a [Repository] local to the build folder.
         */
        fun projectLocalRepository(project: Project): Repository = Repository(
            name = "ProjectLocal",
            url =
                project.layout.buildDirectory
                    .dir("maven")
                    .map { it.asFile.toURI() },

            user = project.objects.property(),
            password = project.objects.property(),
        )
    }
}
