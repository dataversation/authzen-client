/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class SubjectSearchResponse(
    val results: List<Subject> = emptyList(),
    val page: PaginationResponse? = null
)
