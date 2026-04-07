/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */

package com.dataversation.authzen.model

/**
 * Request for a single access evaluation — `POST /access/v1/evaluation`.
 *
 * Asks the PDP whether the [subject] is permitted to perform the [action] on the [resource].
 *
 * When used as a top-level request, [subject], [action], and [resource] are REQUIRED.
 * When nested inside [EvaluationsRequest.evaluations], any field may be omitted to inherit
 * the corresponding default from the parent request.
 *
 * Defined in [AuthZEN 1.0 Section 4.1](https://openid.net/specs/authorization-api-1_0.html#section-4.1).
 *
 * @property subject The user or principal requesting access (REQUIRED at top level).
 * @property action The operation being requested (REQUIRED at top level).
 * @property resource The target of the access request (REQUIRED at top level).
 * @property context Environmental or situational data (OPTIONAL). May include time of day,
 *   request location, PEP capabilities, or schema definitions.
 */
data class EvaluationRequest(
    val subject: Subject? = null,
    val action: Action? = null,
    val resource: Resource? = null,
    val context: Map<String, Any?>? = null
)
