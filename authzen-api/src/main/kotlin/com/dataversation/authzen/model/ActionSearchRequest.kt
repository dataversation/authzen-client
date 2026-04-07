/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Request for the Action Search API — `POST /access/v1/search/action`.
 *
 * Discovers all actions the [subject] is permitted to perform on the [resource].
 * Results are transitive: permissions through group membership or other relationships
 * are included.
 *
 * Defined in [AuthZEN 1.0 Section 5.3](https://openid.net/specs/authorization-api-1_0.html#section-5.3).
 *
 * @property subject The principal whose permitted actions are being queried (REQUIRED).
 * @property resource The target resource (REQUIRED).
 * @property action Not used in the spec for action search, but included for forward compatibility.
 * @property context Environmental data (OPTIONAL).
 * @property page Pagination parameters for large result sets (OPTIONAL).
 */
data class ActionSearchRequest(
    val subject: Subject? = null,
    val resource: Resource? = null,
    val action: Action? = null,
    val context: Map<String, Any?>? = null,
    val page: PaginationRequest? = null
)
