/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * The target resource of an authorization request.
 *
 * Defined in [AuthZEN 1.0 Section 3.2](https://openid.net/specs/authorization-api-1_0.html#section-3.2).
 *
 * @property type The resource category (REQUIRED). Used to disambiguate identifiers across
 *   different resource types (e.g. `"document"`, `"account"`, `"zaak"`).
 * @property id A unique identifier for the resource, scoped to [type] (REQUIRED).
 * @property properties Resource attributes and metadata as key-value pairs (OPTIONAL).
 *   May include ownership, state, classification, or any other application-specific data
 *   needed for policy evaluation. Values may be simple types or complex (arrays, objects).
 */
data class Resource(
    val type: String = "",
    val id: String = "_",
    val properties: Map<String, Any?>? = null
)
