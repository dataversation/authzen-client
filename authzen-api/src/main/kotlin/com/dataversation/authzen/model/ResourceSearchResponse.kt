/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class ResourceSearchResponse(
    val results: List<Resource> = emptyList(),
    val page: PaginationResponse? = null
)
