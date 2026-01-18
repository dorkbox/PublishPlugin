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

package dorkbox.gradlePublish.portal.impl.auth

class ApiKeyAuth(private val location: String, val paramName: String) : Authentication {
    var apiKey: String? = null
    var apiKeyPrefix: String? = null

    override fun apply(query: MutableMap<String, List<String>>, headers: MutableMap<String, String>) {
        val key: String = apiKey ?: return
        val prefix: String? = apiKeyPrefix
        val value: String = if (prefix != null) "$prefix $key" else key
        when (location) {
            "query" -> query[paramName] = listOf(value)
            "header" -> headers[paramName] = value
        }
    }
}
