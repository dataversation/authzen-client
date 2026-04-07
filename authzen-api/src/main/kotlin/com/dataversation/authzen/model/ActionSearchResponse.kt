/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Response from the Action Search API.
 *
 * Each returned [Action] represents an operation the subject is permitted to perform
 * on the resource. When used in an [EvaluationRequest], these actions should result
 * in `decision = true` (though time-dependent policies may cause divergence).
 *
 * @property results The list of permitted actions.
 * @property page Pagination metadata (REQUIRED if the result set is partial).
 */
data class ActionSearchResponse(
    val results: List<Action> = emptyList(),
    val page: PaginationResponse? = null
)
