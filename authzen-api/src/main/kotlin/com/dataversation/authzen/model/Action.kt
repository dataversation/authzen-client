/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class Action(
    val name: String = "",
    val properties: Map<String, Any?>? = null
)
