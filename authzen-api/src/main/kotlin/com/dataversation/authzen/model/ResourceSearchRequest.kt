/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Request for the Resource Search API — `POST /access/v1/search/resource`.
 *
 * Discovers all resources of the specified [type][Resource.type] on which the [subject]
 * is permitted to perform the [action]. The resource's [id][Resource.id] SHOULD be
 * omitted and MUST be ignored by the PDP if present.
 *
 * Defined in [AuthZEN 1.0 Section 5.2](https://openid.net/specs/authorization-api-1_0.html#section-5.2).
 *
 * @property subject The principal whose permissions are being queried (REQUIRED).
 * @property action The operation to check permissions for (REQUIRED).
 * @property resource Specifies the resource [type][Resource.type] to search for (REQUIRED).
 *   The [id][Resource.id] SHOULD be omitted.
 * @property context Environmental data (OPTIONAL).
 * @property page Pagination parameters for large result sets (OPTIONAL).
 */
data class ResourceSearchRequest(
    val subject: Subject? = null,
    val action: Action? = null,
    val resource: Resource? = null,
    val context: Map<String, Any?>? = null,
    val page: PaginationRequest? = null
)
