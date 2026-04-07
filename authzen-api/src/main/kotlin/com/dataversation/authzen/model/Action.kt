/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * The type of access being requested in an authorization evaluation.
 *
 * Defined in [AuthZEN 1.0 Section 3.3](https://openid.net/specs/authorization-api-1_0.html#section-3.3).
 *
 * @property name The action identifier (REQUIRED). Represents the operation being requested
 *   (e.g. `"can_read"`, `"can_edit"`, `"lezen"`, `"wijzigen"`).
 * @property properties Action-specific parameters and attributes as key-value pairs (OPTIONAL).
 *   Values may be simple types or complex (arrays, objects).
 */
data class Action(
    val name: String = "",
    val properties: Map<String, Any?>? = null
)
