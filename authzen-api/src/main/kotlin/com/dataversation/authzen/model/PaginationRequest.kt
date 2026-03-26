/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class PaginationRequest(
    val token: String? = null,
    val limit: Int? = null,
    val properties: Map<String, Any?>? = null
)
