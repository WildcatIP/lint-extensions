/*
 * Copyright 2017 Post Social Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.other.android.lint

import com.android.tools.lint.client.api.IssueRegistry

/*
 * Re-implementation of an idea I encountered at Twitter.
 */
@SuppressWarnings("unused")
class CustomIssueRegistry : IssueRegistry() {
    override val issues = listOf(
            MISSING_NULLITY_ANNOTATION,
            UNNECESSARY_NULLITY_ANNOTATION,
            IMMUTABLE_CLASS,
            BLACKLISTED_CONSTRUCTOR,
            BLACKLISTED_METHOD,
            BLACKLISTED_BASE_CLASS,
            BLACKLISTED_ANNOTATION
    )
}
