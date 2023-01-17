/*
 * Copyright 2021 dorkbox, llc
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
import org.gradle.api.*
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.signatory.internal.pgp.InMemoryPgpSignatoryProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*


/**
 * For managing (what should be common sense) gradle tasks, such as:
 *  - publishing gradle projects to sonatype
 */
@Suppress("UnstableApiUsage", "unused")
class PublishPlugin : Plugin<Project> {
    companion object {
        val DTF = DateTimeFormatter.ofPattern("E MMM HH:mm:ss 'UTC' yyyy").withZone(ZoneOffset.UTC)

        init {
            // To fix maven+gradle moronic incompatibilities: https://github.com/gradle/gradle/issues/11308
            System.setProperty("org.gradle.internal.publish.checksums.insecure", "true")
        }

        /**
         * If the kotlin plugin is applied, and there is a compileKotlin task.. Then kotlin is enabled
         * NOTE: This can ONLY be called from a task, it cannot be called globally!
         */
        fun hasKotlin(project: Project, debug: Boolean = false): Boolean {
            try {
                // check if plugin is available
                project.plugins.findPlugin("org.jetbrains.kotlin.jvm") ?: return false

                if (debug) println("\tHas kotlin plugin")

                // this will check if the task exists, and throw an exception if it does not or return false
                project.tasks.named("compileKotlin", KotlinCompile::class.java).orNull ?: return false

                if (debug) println("\tHas compile kotlin task")

                // check to see if we have any kotlin file
                val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
                val main = sourceSets.getByName("main")
                val kotlin = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin

                if (debug) {
                    println("\tmain dirs: ${main.java.srcDirs}")
                    println("\tkotlin dirs: ${kotlin.srcDirs}")

                    project.buildFile.parentFile.walkTopDown().filter { it.extension == "kt" }.forEach {
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
    }

    private lateinit var project: Project

    @Volatile
    private var hasMavenOutput = false

    // this is lazy, because it MUST be run from a task!
    private val hasKotlin: Boolean by lazy { hasKotlin(project) }

    @Suppress("ObjectLiteralToLambda")
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

        val sourceJar = project.tasks.create("sourceJar", Jar::class.java).apply {
            description = "Creates a JAR that contains the source code."
            archiveClassifier.set("sources")
            mustRunAfter(project.tasks.getByName("jar"))
            duplicatesStrategy = DuplicatesStrategy.FAIL
        }

        val javaDocJar = project.tasks.create("javaDocJar", Jar::class.java).apply {
            description = "Creates a JAR that contains the javadocs."
            // nothing in javadocs. sources is all we care about
            archiveClassifier.set("javadoc")
            mustRunAfter(project.tasks.getByName("jar"))
            from("")
        }

        // this makes sure that we run this AFTER all the info in the project has been figured out, but before it's run (so we can still modify it)
        project.afterEvaluate {
            project.tasks.getByName("sourceJar").apply {
                val task = this as Jar
//                println("Configuring jar sources: ${task.name}")

                val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
                val mainSourceSet: SourceSet = sourceSets.getByName("main")

                if (hasKotlin) {
//                    println("Kotlin sources: ${task.name}")
                    // want to included java + kotlin for the sources

                    // kotlin stuff. Sometimes kotlin depends on java files, so the kotlin source-sets have BOTH java + kotlin.
                    // we want to make sure to NOT have both, as it will screw up creating the jar!
                    try {
                        val kotlin = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin

                        val srcDirs = kotlin.srcDirs
                        val kotlinFiles = kotlin.asFileTree.matching { it: PatternFilterable ->
                            // find out if this file (usually, just a java file) is ALSO in the java source-set.
                            // this is to prevent DUPLICATES in the jar, because sometimes kotlin must be .kt + .java in order to compile!
                            val javaFiles = mainSourceSet.java.files.map { file ->
                                // by definition, it MUST be one of these
                                val base = srcDirs.first {
                                    // find out WHICH src dir base path it is
                                    val path = project.buildDir.relativeTo(it)
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
                    } catch (ignored: Exception) {
                        // maybe we don't have kotlin for the project
                    }
                }

                // kotlin is always compiled first
//                println("Java sources: ${task.name}")
//                mainSourceSet.java.files.forEach {
//                    println("\t$it")
//                }
                task.from(mainSourceSet.java)
            }
        }

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
                            assignFromProp("sonatypePrivateKeyFile", "") { fileName ->
                                project.extensions.extraProperties["sonatypePrivateKeyFile"] = fileName

                                if (project.extensions.extraProperties.has("sonatypePrivateKeyPassword")) {
                                    val password = project.extensions.extraProperties["sonatypePrivateKeyPassword"] as String
                                    val fileText = File(fileName).readText()
                                    if (fileText.isNotEmpty()) {
                                        sign.apply {
                                            useInMemoryPgpKeys(fileText, password)
                                        }
                                    }
                                }
                            }

                            assignFromProp("sonatypePrivateKeyPassword", "") { password ->
                                project.extensions.extraProperties["sonatypePrivateKeyPassword"] = password

                                if (project.extensions.extraProperties.has("sonatypePrivateKeyFile")) {
                                    val fileName = project.extensions.extraProperties["sonatypePrivateKeyFile"] as String
                                    if (fileName.isNotEmpty()) {
                                        val fileText = File(fileName).readText()
                                        if (fileText.isNotEmpty()) {
                                            sign.apply {
                                                useInMemoryPgpKeys(fileText, password)
                                            }
                                        }
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

                mavPub.artifact(sourceJar)

                mavPub.artifact(javaDocJar)
            }
        }

        project.tasks.create("getSonatypeUrl").apply {
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }

            group = "publish and release"

            this.doLast(object: Action<Task> {
                override fun execute(task: Task) {
                    val url = "https://oss.sonatype.org/content/repositories/releases/"
                    val projectName = config.groupId.replace('.', '/')

                    // output the release URL in the console
                    println("\tSonatype URL: $url$projectName/${config.name}/${config.version}/")
                }
            })
        }

        project.tasks.getByName("publishToMavenLocal").apply {
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }

            group = "publish and release"
        }

        project.tasks.create("publishToSonatypeAndRelease").apply {
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }

            group = "publish and release"
            description = "Publish and Release this project to the Sonatype Maven repository"

            dependsOn("publishToMavenLocal", "publishToSonatype", "closeAndReleaseRepository")
        }

        // (when the dependencies are there) we want to ALWAYS run maven local FIRST.
        project.tasks.getByName("publishToSonatype").mustRunAfter(project.tasks.getByName("publishToMavenLocal"))
        project.tasks.getByName("closeAndReleaseRepository").mustRunAfter(project.tasks.getByName("publishToSonatype"))


        // only add files to the PRIMARY jar if we are deploying to maven
        // this is a LITTLE HACKY, but we have to modify the task graph BEFORE the task graph is calculated...
        val taskNames = project.gradle.startParameter.taskNames
        hasMavenOutput = taskNames.contains("publishToMavenLocal") || taskNames.contains("publishToSonatypeAndRelease")

        if (hasMavenOutput) {
            project.tasks.getByName("jar").apply {
                dependsOn("generatePomFileForMavenPublication")

                outputs.upToDateWhen { false }
                outputs.cacheIf { false }
            }
        }

        project.tasks.withType<PublishToMavenLocal> {
            doFirst {
                println("\tPublishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to Maven Local")
                publication.artifacts.forEach {
                    println("\t\t${it.file}")
                }
            }

            onlyIf {
                val pub = get()
                publication == pub.publications.getByName("maven")
            }
        }

        project.tasks.withType<PublishToMavenRepository> {
             doFirst {
                 val url = "https://oss.sonatype.org/content/repositories/releases/"
                 val projectName = config.groupId.replace('.', '/')

                 // output the release URL in the console
                 println("\tPublishing '${publication.groupId}:${publication.artifactId}:${publication.version}' to $url$projectName/${config.name}/${config.version}/")
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
            nexusStaging {
                assignFromProp("sonatypeUserName", config.sonatype.userName) { username = it }
                assignFromProp("sonatypePassword", config.sonatype.password) { password = it }
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
                // only sign if we have something configured for signing!
                if (project.extensions.extraProperties.has("sonatypePrivateKeyFile")) {
                    sign((project.extensions.getByName("publishing") as PublishingExtension).publications.getByName("maven"))
                }
            }

            // output how much the time-outs are
            val durationString = config.httpTimeout.toString().substring(2)
                    .replace("(\\d[HMS])(?!$)", "$1 ").lowercase(Locale.getDefault())


            val fullReleaseTimeout = Duration.ofMillis(config.retryDelay.toMillis() * config.retryLimit)
            val fullReleaseString = fullReleaseTimeout.toString().substring(2)
                    .replace("(\\d[HMS])(?!$)", "$1 ").lowercase(Locale.getDefault())

            project.tasks.findByName("publishToSonatype")?.doFirst {
                println("\tPublishing to Sonatype: ${config.groupId}:${config.artifactId}:${config.version}")
                println("\t\tSonatype HTTP timeout: $durationString")
                println("\t\tSonatype API timeout: $fullReleaseString")
            }

            if (hasMavenOutput) {
                (project.tasks.getByName("jar") as Jar).apply {
                    // we have to generate the POM file BEFORE the jar (so the outputs are available!)
                    val pomFileTask = project.tasks.getByName("generatePomFileForMavenPublication")
                    val pomFile = pomFileTask.outputs.files.first()
                    val pomProps = pomFile.parentFile.resolve("pom.properties")

                    from(pomFile) {
                        it.into("META-INF/maven/${project.group}/${project.name}")
                        it.rename { "pom.xml" }
                    }

                    from(pomProps) {
                        it.into("META-INF/maven/${project.group}/${project.name}")
                    }

                    this.doFirst(object: Action<Task> {
                        override fun execute(task: Task) {
                            // have to write out the pom.properties file
                            pomProps.writeText(
                                "#Generated by Dorkbox\n" +
                                "#${DTF.format(Instant.now())}\n" +
                                "version=${config.version}\n" +
                                "groupId=${config.groupId}\n" +
                                "artifactId=${config.artifactId}\n")
                        }
                    })
                }
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


    @Suppress("UNCHECKED_CAST", "RemoveExplicitTypeArguments")
    private fun assignFromProp(propertyName: String, defaultValue: String, apply: (value: String)->Unit) {
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
        val loaderFunctions: ArrayList<Plugin<Pair<String, String>>>?
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
