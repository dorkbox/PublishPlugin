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

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

object ProjectExtensions {
    /**
     * If the kotlin plugin is applied, and there is a compileKotlin task.. Then kotlin is enabled
     * NOTE: This can ONLY be called from a task, it cannot be called globally!
     */
    fun Project.hasKotlin(debug: Boolean = false): Boolean {
        try {
            // check if plugin is available
            plugins.findPlugin("org.jetbrains.kotlin.jvm") ?: return false

            if (debug) println("\tHas kotlin plugin")

            // this will check if the task exists, and throw an exception if it does not or return false
            tasks.named("compileKotlin", KotlinCompile::class.java).orNull ?: return false

            if (debug) println("\tHas compile kotlin task")

            // check to see if we have any kotlin file
            val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
            val main = sourceSets.getByName("main")
            val kotlin = extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin

            if (debug) {
                println("\tmain dirs: ${main.java.srcDirs}")
                println("\tkotlin dirs: ${kotlin.srcDirs}")

                buildFile.parentFile.walkTopDown().filter { it.extension == "kt" }.forEach {
                    println("\t\t$it")
                }
            }

            val files = main.java.srcDirs + kotlin.srcDirs
            files.forEach { srcDir ->
                val kotlinFile = srcDir.walkTopDown().find { it.extension == "kt" }
                if (kotlinFile?.exists() == true) {
                    if (debug) println("\t Has kotlin file: $kotlinFile")
                    return true
                }
            }
        } catch (e: Exception) {
            if (debug) e.printStackTrace()
        }

        return false
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
    fun Project.warnIfCredentialsAreMissing(repository: Repository) {
        if (repository.user.orNull == null) {
            logger.warn(
                "No username configured for repository {} at {}.",
                repository.name,
                repository.url,
            )
        }
        if (repository.password.orNull == null) {
            logger.warn(
                "No password configured for user {} on repository {} at {}.",
                repository.user.orNull,
                repository.name,
                repository.url,
            )
        }
    }
}
