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

package dorkbox.gradlePublish.portal.impl.infrastructure

/**
 * Defines a config object for a given request
 *
 * NOTE: This object doesn't include 'body' because it
 *       allows for caching of the constructed object
 *       for many request definitions.
 *
 * NOTE: Headers is a Map<String,String> because rfc2616 defines
 *       multi-valued headers as csv-only.
 */
data class RequestConfig<T>(
    val method: RequestMethod,
    val path: String,
    val headers: MutableMap<String, String> = mutableMapOf(),
    val params: MutableMap<String, Any> = mutableMapOf(),
    val query: MutableMap<String, List<String>> = mutableMapOf(),
    val requiresAuthentication: Boolean,
    val body: T? = null
)
