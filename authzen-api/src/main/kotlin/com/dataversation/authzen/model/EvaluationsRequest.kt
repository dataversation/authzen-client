/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class EvaluationsRequest(
    val subject: Subject? = null,
    val action: Action? = null,
    val resource: Resource? = null,
    val context: Map<String, Any?>? = null,
    val options: Map<String, Any?>? = null,
    val evaluations: List<EvaluationRequest> = emptyList()
)
