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


package dorkbox.gradlePublish.portal.api

/*
 * Copyright 2026 Danilo Pianini
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
 * https://github.com/DanySK/maven-central-portal-kotlin-api
 */

import dorkbox.gradlePublish.portal.api.PublishingApi.PublishingTypeApiV1PublisherUploadPost
import dorkbox.gradlePublish.portal.impl.infrastructure.HttpResponse
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.ContentDisposition

/**
 * Uploads a deployment bundle intended for publication to Maven Central.
 *
 * @param name The name of the deployment or bundle. If not specified, the name of the attached file is used.
 * @param releaseAfterUpload If `true`, the deployment will automatically proceed to the `PUBLISHING` state upon validation.
 *                           If `false`, it will remain in the `VALIDATED` state until manually approved. Default is `false`.
 * @param bundle The bundle file to upload.
 * @return A [String] response from the server.
 */
@Suppress("UNCHECKED_CAST")
suspend fun PublishingApi.apiV1PublisherUploadPost(
    name: String,
    releaseAfterUpload: Boolean = false,
    bundle: InputProvider,
): HttpResponse<String> = apiV1PublisherUploadPost(
    name,
    if (releaseAfterUpload) PublishingTypeApiV1PublisherUploadPost.AUTOMATIC else PublishingTypeApiV1PublisherUploadPost.USER_MANAGED,
    FormPart("bundle", bundle, Headers.build { append(ContentDisposition, "filename=\"$name\"") })
)
