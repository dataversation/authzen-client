/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class PaginationResponse(
    val nextToken: String? = null,
    val count: Long? = null,
    val total: Long? = null,
    val properties: Map<String, Any?>? = null
)
