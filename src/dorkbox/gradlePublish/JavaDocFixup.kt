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
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withType

object JavaDocFixup {
    fun Project.fixJavadocJar(config: PublishToMavenCentral) {
        // fixup javadocs

        // org.danilopianini.gradle.mavencentral.tasks.JavadocJar
        // cannot use withType<JavadocJar> here, it won't work.
        tasks.named("javadocJar") {
            it.group = "documentation"
            it as Jar

            it.exclude("LICENSE", "LICENSE.blob")
        }

        if (config.enableDokka) {
           return
        }

        // React to Dokka documentation (part of the org.danilopianini.gradle task, but we DO NOT use it...)
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { _ ->
            if (!pluginManager.hasPlugin("org.jetbrains.dokka")) {
                tasks.withType<Javadoc> {
                    // Clear all source sets to make it run with no input
                    source = files().asFileTree
                }
            }
        }
    }
}
