/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Pagination metadata in Search API responses.
 *
 * Defined in [AuthZEN 1.0 Section 5](https://openid.net/specs/authorization-api-1_0.html#section-5).
 *
 * @property nextToken Opaque continuation token (REQUIRED in paginated responses).
 *   An empty string signals that there are no more results. Pass this value as
 *   [PaginationRequest.token] to fetch the next page.
 * @property count Number of results in this response page (OPTIONAL).
 * @property total Total number of matching results across all pages (OPTIONAL).
 * @property properties Implementation-specific pagination metadata (OPTIONAL).
 */
data class PaginationResponse(
    val nextToken: String? = null,
    val count: Long? = null,
    val total: Long? = null,
    val properties: Map<String, Any?>? = null
)
