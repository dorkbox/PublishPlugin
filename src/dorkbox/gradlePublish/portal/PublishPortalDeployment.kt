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

package dorkbox.gradlePublish.portal

import dorkbox.gradlePublish.portal.api.PublishingApi
import dorkbox.gradlePublish.portal.api.apiV1PublisherUploadPost
import dorkbox.gradlePublish.portal.impl.infrastructure.HttpResponse
import dorkbox.gradlePublish.portal.impl.models.DeploymentResponseFiles
import dorkbox.gradlePublish.portal.impl.models.DeploymentResponseFiles.DeploymentState.*
import io.ktor.client.request.forms.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Lazy class acting as a container for stateful operations on Maven Central Portal.
 */
data class PublishPortalDeployment(
    private val project: Project,
    private val baseUrl: String,
    private val user: Provider<String>,
    private val password: Provider<String>,
    private val zipTask: TaskProvider<out ConventionTask>,
) {
    /**
     * The Publishing portal client.
     */
    val client: PublishingApi by lazy {
        PublishingApi(baseUrl).apply {
            check(user.isPresent) {
                "Username for the central portal at $baseUrl is not set."
            }
            check(password.isPresent) {
                "Password for the central portal at $baseUrl is not set."
            }

            setUsername(user.get())
            setPassword(password.get())
        }
    }

    /**
     * THe zip file to upload.
     */
    val fileToUpload: File by lazy {
        zipTask.get().outputs.files.singleFile.apply {
            check(exists() && isFile) {
                "File $absolutePath does not exist or is not a file, did task ${zipTask.name} run?"
            }
        }
    }

    /**
     * Uploads a bundle to the Central Portal, returning the upload id.
     */
    @JvmOverloads
    suspend fun upload(bundle: File, name: String = bundle.name, releaseAfterUpload: Boolean = false): String {
        val response = client.apiV1PublisherUploadPost(
            name,
            releaseAfterUpload = releaseAfterUpload,
            bundle = InputProvider(bundle.length()) { bundle.inputStream().asInput() },
        )

        return when (response.status) {
            OK,
            CREATED -> {
                println("\tBundle from file ${bundle.path} uploaded successfully")
                response.body()
            }

            INTERNAL_SERVER_ERROR -> error("Error on bundle upload")

            else -> maybeUnauthorized("upload", response)
        }
    }

    /**
     * Lazily computed staging repository descriptor.
     */
    val deploymentId: String by lazy {
        when (val idFromProperty = project.properties[PUBLISH_DEPLOYMENT_ID_PROPERTY_NAME]) {
            null -> {
                runBlocking { upload(fileToUpload) }
            }
            else ->
                idFromProperty.toString().also {
                    project.logger.lifecycle("Using existing deployment id {}", it)
                }
        }
    }

    private suspend fun deploymentStatus(): DeploymentResponseFiles {
        val response = client.apiV1PublisherStatusPost(deploymentId)

        println("\tDeployment status: " + response.body())
        val body =
            when (response.status) {
                OK -> response.typedBody<DeploymentResponseFiles>(typeInfo<DeploymentResponseFiles>())
                INTERNAL_SERVER_ERROR -> error("Error on deployment $deploymentId status query")
                else -> maybeUnauthorized("deployment status check", response)
            }
        return body
    }

    /**
     * Validates the deployment.
     */
    tailrec suspend fun validate(waitAmongRetries: Duration = waitingTime, showLog: Boolean = true) {
        if (showLog) {
            println("\tValidating deployment $deploymentId on Central Portal at $baseUrl")
        }

        val responseBody = deploymentStatus()
        when (responseBody.deploymentState) {
            PENDING,
            VALIDATING   -> {
                delay(waitAmongRetries)
                validate(waitAmongRetries * 2, false)
            }

            VALIDATED,
            PUBLISHING,
            PUBLISHED   -> println("\tDeployment $deploymentId validated")

            FAILED      -> error("Deployment $deploymentId validation FAILED: $responseBody")

            null        -> error("Unexpected/unknown deployment state null for deployment $deploymentId")
        }
    }

    /**
     * Releases the deployment.
     */
    tailrec suspend fun release(waitAmongRetries: Duration = waitingTime): Unit =
        when (deploymentStatus().deploymentState) {
            null       -> error("Unexpected/unknown deployment state null for deployment $deploymentId")

            PENDING,
            VALIDATING,
            PUBLISHING -> {
                delay(waitAmongRetries)
                release(waitAmongRetries * 2)
            }

            PUBLISHED  -> println("\tDeployment $deploymentId has been already released", )

            VALIDATED  -> {
                println("\tReleasing deployment $deploymentId")

                val releaseResponse = client.apiV1PublisherDeploymentDeploymentIdPost(deploymentId)
                when (releaseResponse.status) {
                    NO_CONTENT -> println("\tDeployment $deploymentId released", )
                    NOT_FOUND -> error("Deployment $deploymentId not found. $releaseResponse")
                    INTERNAL_SERVER_ERROR -> error("Internal server error when releasing $deploymentId: $releaseResponse")
                    else -> maybeUnauthorized("deployment release", releaseResponse)
                }
            }

            FAILED     -> error("Deployment $deploymentId validation FAILED")
        }

    /**
     * Drops the repository. Must be called after close().
     */
    tailrec suspend fun drop(waitAmongRetries: Duration = waitingTime): Unit =
        when (deploymentStatus().deploymentState) {
            null       -> error("Unexpected/unknown deployment state null for deployment $deploymentId")
            PENDING,
            VALIDATING,
            PUBLISHING -> {
                delay(waitAmongRetries)
                drop(waitAmongRetries * 2)
            }

            PUBLISHED  -> error("Deployment $deploymentId has been published already and cannot get dropped")

            FAILED,
            VALIDATED  -> {
                println("\tDropping deployment $deploymentId", )

                val releaseResponse = client.apiV1PublisherDeploymentDeploymentIdDelete(deploymentId)
                when (releaseResponse.status) {
                    NO_CONTENT -> println("\tDeployment $deploymentId dropped", )
                    NOT_FOUND -> error("Deployment $deploymentId not found. $releaseResponse")
                    INTERNAL_SERVER_ERROR -> error("Internal server error when dropping $deploymentId: $releaseResponse")
                    else -> maybeUnauthorized("deployment release", releaseResponse)
                }
            }
        }

    /**
     * Constants for the Central Portal Deployments.
     */
    companion object {
        /**
         * The property name for the deployment id.
         */
        const val PUBLISH_DEPLOYMENT_ID_PROPERTY_NAME = "publishDeploymentId"

        /**
         * The bundle validation task name.
         */
        const val VALIDATE_TASK_NAME = "validateMavenCentralPortalPublication"

        /**
         * The bundle drop task name.
         */
        const val DROP_TASK_NAME = "dropMavenCentralPortalPublication"

        /**
         * The bundle release task name.
         */
        const val RELEASE_TASK_NAME = "releaseMavenCentralPortalPublication"

        private const val OK = 200
        private const val CREATED = 201
        private const val NO_CONTENT = 204
        private const val BAD_REQUEST = 400
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val INTERNAL_SERVER_ERROR = 500

        private val waitingTime: Duration = 1.seconds

        private fun maybeUnauthorized(action: String, response: HttpResponse<*>): Nothing = when (response.status) {
            BAD_REQUEST -> error("Authentication failure, make sure that your credentials are correct")
            UNAUTHORIZED -> error("No active session or not authenticated, check your credentials")
            FORBIDDEN -> error("User unauthorized to perform the $action action")
            else -> error("Unexpected response $response")
        }
    }
}
