/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Response from a single access evaluation.
 *
 * Defined in [AuthZEN 1.0 Section 4.1](https://openid.net/specs/authorization-api-1_0.html#section-4.1).
 *
 * @property decision `true` permits access, `false` denies it (REQUIRED).
 *   A `false` decision is a successful evaluation resulting in denial — not an error.
 * @property context Additional enforcement information (OPTIONAL). May convey reasons for
 *   the decision, obligations, UI rendering hints, step-up authentication requirements,
 *   or other PDP-specific metadata.
 */
data class EvaluationResponse(
    val decision: Boolean = false,
    val context: Map<String, Any?>? = null
)
