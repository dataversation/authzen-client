/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen

import com.dataversation.authzen.model.ActionSearchRequest
import com.dataversation.authzen.model.ActionSearchResponse
import com.dataversation.authzen.model.EvaluationRequest
import com.dataversation.authzen.model.EvaluationResponse
import com.dataversation.authzen.model.EvaluationsRequest
import com.dataversation.authzen.model.EvaluationsResponse
import com.dataversation.authzen.model.ResourceSearchRequest
import com.dataversation.authzen.model.ResourceSearchResponse
import com.dataversation.authzen.model.SubjectSearchRequest
import com.dataversation.authzen.model.SubjectSearchResponse

/**
 * Policy Decision Point (PDP) interface as defined by the
 * [AuthZEN Authorization API 1.0](https://openid.net/specs/authorization-api-1_0.html).
 *
 * A PDP evaluates authorization requests from a Policy Enforcement Point (PEP) and returns
 * access decisions. Implementations may delegate to an external PDP over HTTP/gRPC, or
 * evaluate policies in-process.
 *
 * The Access Evaluation API ([evaluation]) is REQUIRED for all implementations.
 * The Access Evaluations API ([evaluations]) and the Search APIs ([searchActions],
 * [searchSubjects], [searchResources]) are OPTIONAL — implementations that do not support
 * them should throw [UnsupportedOperationException].
 */
interface AccessService {

    /**
     * Access Evaluation API — `POST /access/v1/evaluation`.
     *
     * Evaluates a single authorization request: whether the [subject][EvaluationRequest.subject]
     * is permitted to perform the [action][EvaluationRequest.action] on the
     * [resource][EvaluationRequest.resource].
     *
     * Returns a [decision][EvaluationResponse.decision] of `true` (permit) or `false` (deny),
     * with optional [context][EvaluationResponse.context] conveying reasons, obligations, or
     * enforcement metadata.
     *
     * A `false` decision is a successful evaluation resulting in denial — not an error.
     * Errors should be signaled via exceptions.
     */
    fun evaluation(request: EvaluationRequest): EvaluationResponse

    /**
     * Access Evaluations API — `POST /access/v1/evaluations`.
     *
     * Evaluates multiple authorization requests ("boxcarring") in a single call. Top-level
     * [subject][EvaluationsRequest.subject], [action][EvaluationsRequest.action], and
     * [resource][EvaluationsRequest.resource] provide defaults that individual
     * [evaluations][EvaluationsRequest.evaluations] may override.
     *
     * The response contains one [EvaluationResponse] per request item, in the same order.
     *
     * Evaluation semantics can be controlled via [EvaluationsRequest.options]:
     * - `execute_all` (default): process all requests, return all results
     * - `deny_on_first_deny`: short-circuit on first denial
     * - `permit_on_first_permit`: short-circuit on first permit
     */
    fun evaluations(request: EvaluationsRequest): EvaluationsResponse

    /**
     * Action Search API — `POST /access/v1/search/action`.
     *
     * Returns all actions the [subject][ActionSearchRequest.subject] is permitted to perform
     * on the [resource][ActionSearchRequest.resource]. The request does not include an action
     * field — the PDP discovers all permitted actions.
     *
     * Results are transitive: if the subject has permissions through group membership or
     * other relationships, those actions should be included.
     *
     * Supports [pagination][ActionSearchRequest.page] for large result sets.
     */
    fun searchActions(request: ActionSearchRequest): ActionSearchResponse

    /**
     * Subject Search API — `POST /access/v1/search/subject`.
     *
     * Returns all subjects of the specified [type][SubjectSearchRequest.subject] that are
     * permitted to perform the [action][SubjectSearchRequest.action] on the
     * [resource][SubjectSearchRequest.resource].
     *
     * The subject's [id][com.dataversation.authzen.model.Subject.id] SHOULD be omitted
     * in the request and MUST be ignored by the PDP if present.
     *
     * Supports [pagination][SubjectSearchRequest.page] for large result sets.
     */
    fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse

    /**
     * Resource Search API — `POST /access/v1/search/resource`.
     *
     * Returns all resources of the specified [type][ResourceSearchRequest.resource] on which the
     * [subject][ResourceSearchRequest.subject] is permitted to perform the
     * [action][ResourceSearchRequest.action].
     *
     * The resource's [id][com.dataversation.authzen.model.Resource.id] SHOULD be omitted
     * in the request and MUST be ignored by the PDP if present.
     *
     * Supports [pagination][ResourceSearchRequest.page] for large result sets.
     */
    fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse
}
