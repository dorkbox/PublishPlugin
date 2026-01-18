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

import dorkbox.gradlePublish.ProjectExtensions.hasKotlin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

object SourcesJarFixup {
    fun Project.fixSourcesJar() {
        // Combine Kotlin and Java sources into a single sources.jar

        val sourceProject = this

        // this makes sure that we run this AFTER all the info in the project has been figured out, but before it's run (so we can still modify it)
        tasks.getByName("sourcesJar").apply {
            this as Jar

            isPreserveFileTimestamps = true
            isReproducibleFileOrder = true
        }

        // doesn't work because the utils plugin is not applied yet
//        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { _ ->
        afterEvaluate {
            // Disable separate kotlin sources jar if it exists
            tasks.findByName("kotlinSourcesJar")?.enabled = false

            // this makes sure that we run this AFTER all the info in the project has been figured out, but before it's run (so we can still modify it)
            tasks.getByName("sourcesJar").apply {
                val task = this as Jar
//                println("Configuring jar sources: ${task.name}")

                val sourceSets = sourceProject.extensions.getByName("sourceSets") as SourceSetContainer
                val mainSourceSet: SourceSet = sourceSets.getByName("main")
                val javaSourcePaths = mainSourceSet.java

                if (hasKotlin()) {
//                    println("Kotlin sources: ${task.name}")
                    // want to included java + kotlin for the sources

                    // kotlin stuff. Sometimes kotlin depends on java files, so the kotlin source-sets have BOTH java + kotlin.
                    // we want to make sure to NOT have both, as it will screw up creating the jar!
                    try {
                        val kotlin = extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin

                        val srcDirs = kotlin.srcDirs
                        val kotlinFiles = kotlin.asFileTree.matching { it: PatternFilterable ->
                            // find out if this file (usually, just a java file) is ALSO in the java source-set.
                            // this is to prevent DUPLICATES in the jar, because sometimes kotlin must be .kt + .java in order to compile!
                            val javaFiles = javaSourcePaths.files.map { file ->
                                // by definition, it MUST be one of these
                                val base = srcDirs.first {
                                    // find out WHICH src dir base path it is
                                    val path = layout.buildDirectory.locationOnly.get().asFile.relativeTo(it)
                                    path.path.isNotEmpty()
                                }
                                // there can be leading "../" (since it's relative. WE DO NOT WANT THAT!
                                val newFile = file.relativeTo(base).path.replace("../", "")
//                                println("\t\tAdding: $newFile")
                                newFile
                            }

                            it.setExcludes(javaFiles)
                        }

//                        kotlinFiles.forEach {
//                            println("\t$it")
//                        }

                        task.from(kotlinFiles)
                    } catch (_: Exception) {
                        // maybe we don't have kotlin for the project
                    }
                }

                // kotlin is always compiled first
//                println("Java sources: ${task.name}")
//                mainSourceSet.java.files.forEach {
//                    println("\t$it")
//                }

                doFirst {
                    println("\tIncluding kotlin + java files in sources.jar")
                }
            }
        }
    }
}
