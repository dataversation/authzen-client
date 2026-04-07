/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Response from the Resource Search API.
 *
 * Each returned [Resource] is one on which the subject is permitted to perform the
 * requested action.
 *
 * @property results The list of matching resources.
 * @property page Pagination metadata (REQUIRED if the result set is partial).
 */
data class ResourceSearchResponse(
    val results: List<Resource> = emptyList(),
    val page: PaginationResponse? = null
)
