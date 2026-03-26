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
 * AuthZEN Authorization API 1.0 Access Service interface.
 * See: https://openid.net/specs/authorization-api-1_0.html
 */
interface AccessService {
    fun evaluation(request: EvaluationRequest): EvaluationResponse
    fun evaluations(request: EvaluationsRequest): EvaluationsResponse
    fun searchActions(request: ActionSearchRequest): ActionSearchResponse
    fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse
    fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse
}
