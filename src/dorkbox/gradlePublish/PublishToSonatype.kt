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

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.plugins.signing.SigningExtension
import java.io.File
import java.util.function.Consumer

class IssueManagement() {
    @get:Input
    var nickname = ""

    @get:Input
    var url = ""
}

class Developer {
    @Input var id = ""
    @Input var name = ""
    @Input var email = ""
}

class Sonatype {
    @Input var userName = ""
    @Input var password = ""
}

class PrivateKey {
    @Input var fileName = ""
    @Input var password = ""
}

open class PublishToSonatype(val project: Project) {
    @get:Input
    var groupId = ""
        set(value) {
            field = value
            pub().groupId = value
        }

    @get:Input
    var artifactId = ""
        set(value) {
            field = value
            pub().artifactId = value
        }

    @get:Input
    var version = ""
        set(value) {
            field = value
            pub().version = value
        }

    @get:Input
    var name = ""
        set(value) {
            field = value
            pom().name.set(value)
        }

    @get:Input
    var description = ""
        set(value) {
            field = value
            pom().description.set(value)
        }

    @get:Input
    var url = ""
        set(value) {
            field = value
            pom().url.set(value)
            pom().scm {
                it.url.set(value)
                it.connection.set("scm:${value}.git")
            }
        }

    @get:Input
    var vendor = ""
        set(value) {
            field = value
            pom().organization {
                it.name.set(value)
            }
        }

    @get:Input
    var vendorUrl = ""
        set(value) {
            field = value
            pom().organization {
                it.url.set(value)
            }
        }

    fun developer(config: Developer.() -> Unit)  {
        pom().developers {
            it.developer { dev ->
                val developer = Developer()
                config(developer)

                dev.id.set(developer.id)
                dev.name.set(developer.name)
                dev.email.set(developer.email)
            }
        }
    }

    fun issueManagement(config: IssueManagement.() -> Unit)  {
        pom().issueManagement {
            val issueMgmt = IssueManagement()
            config(issueMgmt)

            it.system.set(issueMgmt.nickname)
            it.url.set(issueMgmt.url)
        }
    }

    fun privateKey(config: PrivateKey.() -> Unit)  {
        pom().issueManagement {
            val key = PrivateKey()
            config(key)

            val sign = project.extensions.getByName("signing") as SigningExtension
            sign.apply {
                useInMemoryPgpKeys(File(key.fileName).readText(), key.password)
                sign((project.extensions.getByName("publishing") as PublishingExtension).publications.getByName("maven"))
            }
        }
    }

    fun sonatype(config: Sonatype.() -> Unit)  {
        config(sonatype)
    }

    @Input val sonatype = Sonatype()

    fun pub(): MavenPublication {
        return project.extensions.getByType(PublishingExtension::class.java).publications.maybeCreate("maven", MavenPublication::class.java)
    }

    fun pom(): MavenPom {
        return pub().pom!!
    }
}
