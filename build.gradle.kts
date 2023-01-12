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
import java.time.Instant

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!
gradle.startParameter.warningMode = WarningMode.All

plugins {
    java
    `java-gradle-plugin`

//    id("com.gradle.plugin-publish") version "0.14.0"
    id("com.gradle.plugin-publish") version "1.1.0"

    id("com.dorkbox.GradleUtils") version "3.5"
    id("com.dorkbox.Licensing") version "2.17"
    id("com.dorkbox.VersionUpdate") version "2.5"

    kotlin("jvm") version "1.7.0"
}

object Extras {
    // set for the project
    const val description = "Gradle Plugin to publish projects to the sonatype repository"
    const val group = "com.dorkbox"
    const val version = "1.15"

    // set as project.ext
    const val name = "Gradle Publish"
    const val id = "GradlePublish"
    const val vendor = "Dorkbox LLC"
    const val url = "https://git.dorkbox.com/dorkbox/GradlePublish"
    val tags = listOf("sonatype", "publish", "maven")
    val buildDate = Instant.now().toString()
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        author(Extras.vendor)
        url(Extras.url)
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))

            // want to include kotlin files for the source. 'setSrcDirs' resets includes...
            include("**/*.kt")
        }
    }
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // the kotlin version is taken from the plugin, so it is not necessary to set it here
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")


    // publish on sonatype
    // https://plugins.gradle.org/plugin/de.marcphilipp.nexus-publish
    implementation("de.marcphilipp.gradle:nexus-publish-plugin:0.4.0")

    // close and release on sonatype
    // https://plugins.gradle.org/plugin/io.codearte.nexus-staging
    implementation("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.30.0")
}

tasks.jar.get().apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}


/////////////////////////////////
////////    Plugin Publishing + Release
/////////////////////////////////
gradlePlugin {
    plugins {
        create("GradlePublish") {
            id = "${Extras.group}.${Extras.id}"
            implementationClass = "dorkbox.gradlePublish.PublishPlugin"
            displayName = Extras.name
            description = Extras.description
            version = Extras.version
        }
    }
}

pluginBundle {
    website = Extras.url
    vcsUrl = Extras.url
    tags = Extras.tags
}
