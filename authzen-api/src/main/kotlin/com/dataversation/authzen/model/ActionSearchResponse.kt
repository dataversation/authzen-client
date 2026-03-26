/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class ActionSearchResponse(
    val results: List<Action> = emptyList(),
    val page: PaginationResponse? = null
)
