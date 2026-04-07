/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Pagination parameters for Search API requests.
 *
 * Uses opaque token-based pagination. When continuing a previous search, all entity
 * parameters and search criteria MUST match the preceding request.
 *
 * Defined in [AuthZEN 1.0 Section 5](https://openid.net/specs/authorization-api-1_0.html#section-5).
 *
 * @property token Opaque continuation token from a previous [PaginationResponse.nextToken] (OPTIONAL).
 *   Omit for the first page.
 * @property limit Maximum number of results to return (OPTIONAL).
 * @property properties Implementation-specific pagination options such as sorting or
 *   filtering criteria (OPTIONAL).
 */
data class PaginationRequest(
    val token: String? = null,
    val limit: Int? = null,
    val properties: Map<String, Any?>? = null
)
