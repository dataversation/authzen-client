/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

data class EvaluationResponse(
    val decision: Boolean = false,
    val context: Map<String, Any?>? = null
)
