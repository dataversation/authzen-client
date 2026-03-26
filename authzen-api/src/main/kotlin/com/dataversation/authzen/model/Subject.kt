/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class Subject(
    val type: String = "",
    val id: String = "",
    val properties: Map<String, Any?>? = null
)
