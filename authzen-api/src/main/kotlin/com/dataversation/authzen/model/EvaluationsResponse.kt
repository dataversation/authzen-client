/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class EvaluationsResponse(
    val evaluations: List<EvaluationResponse> = emptyList()
)
