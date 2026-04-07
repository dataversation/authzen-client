/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Response from multiple access evaluations.
 *
 * Defined in [AuthZEN 1.0 Section 4.2](https://openid.net/specs/authorization-api-1_0.html#section-4.2).
 *
 * @property evaluations One [EvaluationResponse] per request item, in the same order as
 *   the corresponding [EvaluationsRequest.evaluations]. Individual errors are conveyed
 *   via [EvaluationResponse.context] with `decision = false`; transport-level errors
 *   use HTTP status codes.
 */
data class EvaluationsResponse(
    val evaluations: List<EvaluationResponse> = emptyList()
)
