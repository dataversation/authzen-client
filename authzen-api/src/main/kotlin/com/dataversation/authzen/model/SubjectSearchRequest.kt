/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Request for the Subject Search API — `POST /access/v1/search/subject`.
 *
 * Discovers all subjects of the specified [type][Subject.type] that are permitted to
 * perform the [action] on the [resource]. The subject's [id][Subject.id] SHOULD be
 * omitted and MUST be ignored by the PDP if present.
 *
 * Defined in [AuthZEN 1.0 Section 5.1](https://openid.net/specs/authorization-api-1_0.html#section-5.1).
 *
 * @property subject Specifies the subject [type][Subject.type] to search for (REQUIRED).
 *   The [id][Subject.id] SHOULD be omitted.
 * @property action The operation to check permissions for (REQUIRED).
 * @property resource The target resource (REQUIRED).
 * @property context Environmental data (OPTIONAL).
 * @property page Pagination parameters for large result sets (OPTIONAL).
 */
data class SubjectSearchRequest(
    val subject: Subject? = null,
    val action: Action? = null,
    val resource: Resource? = null,
    val context: Map<String, Any?>? = null,
    val page: PaginationRequest? = null
)
