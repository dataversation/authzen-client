/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Response from the Subject Search API.
 *
 * Each returned [Subject] is permitted to perform the requested action on the resource.
 *
 * @property results The list of matching subjects.
 * @property page Pagination metadata (REQUIRED if the result set is partial).
 */
data class SubjectSearchResponse(
    val results: List<Subject> = emptyList(),
    val page: PaginationResponse? = null
)
