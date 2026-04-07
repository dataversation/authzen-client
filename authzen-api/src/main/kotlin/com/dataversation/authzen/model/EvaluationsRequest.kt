/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Request for multiple access evaluations ("boxcarring") — `POST /access/v1/evaluations`.
 *
 * Top-level [subject], [action], and [resource] provide defaults that individual items
 * in [evaluations] may selectively override. Each field MUST be supplied either at the
 * top level or in every evaluation item.
 *
 * Defined in [AuthZEN 1.0 Section 4.2](https://openid.net/specs/authorization-api-1_0.html#section-4.2).
 *
 * @property subject Default subject for all evaluations (OPTIONAL if each evaluation supplies its own).
 * @property action Default action for all evaluations (OPTIONAL if each evaluation supplies its own).
 * @property resource Default resource for all evaluations (OPTIONAL if each evaluation supplies its own).
 * @property context Default environmental data for all evaluations (OPTIONAL).
 * @property options Evaluation processing options (OPTIONAL). Supports `evaluations_semantic`:
 *   `"execute_all"` (default), `"deny_on_first_deny"`, or `"permit_on_first_permit"`.
 * @property evaluations The list of individual evaluation requests (REQUIRED for boxcarring).
 *   If absent or empty, the request is treated as a single evaluation using the top-level fields.
 */
data class EvaluationsRequest(
    val subject: Subject? = null,
    val action: Action? = null,
    val resource: Resource? = null,
    val context: Map<String, Any?>? = null,
    val options: Map<String, Any?>? = null,
    val evaluations: List<EvaluationRequest> = emptyList()
)
