/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * The subject of an authorization request — the user or machine principal being evaluated.
 *
 * Defined in [AuthZEN 1.0 Section 3.1](https://openid.net/specs/authorization-api-1_0.html#section-3.1).
 *
 * @property type The subject category (REQUIRED). Used to disambiguate identifiers across
 *   different identity systems (e.g. `"user"`, `"service_account"`, `"device"`).
 * @property id A unique identifier for the subject, scoped to [type] (REQUIRED).
 * @property properties Additional attributes as key-value pairs (OPTIONAL). May include
 *   department, group memberships, device identifier, IP address, or any other
 *   application-specific claims. Values may be simple types or complex (arrays, objects).
 */
data class Subject(
    val type: String = "",
    val id: String = "",
    val properties: Map<String, Any?>? = null
)
