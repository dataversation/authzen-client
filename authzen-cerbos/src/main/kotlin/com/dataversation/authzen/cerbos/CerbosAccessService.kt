/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.cerbos

import com.dataversation.authzen.AccessService
import com.dataversation.authzen.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.logging.Level
import java.util.logging.Logger

/**
 * AuthZEN AccessService implementation for [Cerbos](https://cerbos.dev/).
 *
 * Translates AuthZEN Evaluations API requests into Cerbos CheckResources API calls.
 * All actions from the evaluations are grouped into a single Cerbos request per
 * resource, making it efficient for batch evaluation.
 *
 * Cerbos uses its own policy language with resource policies and derived roles.
 * The AuthZEN subject properties are passed as Cerbos principal attributes.
 *
 * @param baseUrl Cerbos URL (e.g. "http://cerbos:3592")
 * @param httpClient Pre-configured OkHttpClient
 */
class CerbosAccessService @JvmOverloads constructor(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : AccessService {

    private val objectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMediaType = "application/json".toMediaType()

    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
        val subject = request.subject
        val resource = request.resource
        val evaluations = request.evaluations

        // This adapter translates batched action names into a single Cerbos CheckResources call.
        // Per-evaluation overrides of subject, resource, or context are not supported because
        // Cerbos CheckResources uses a single principal and resource per batch.
        for (eval in evaluations) {
            if (eval.subject != null) throw UnsupportedOperationException(
                "Per-evaluation subject overrides are not supported by the Cerbos adapter"
            )
            if (eval.resource != null) throw UnsupportedOperationException(
                "Per-evaluation resource overrides are not supported by the Cerbos adapter"
            )
            if (!eval.action?.properties.isNullOrEmpty()) throw UnsupportedOperationException(
                "Action properties are not supported by the Cerbos adapter"
            )
        }

        // Collect unique action names
        val actions = evaluations.mapNotNull { it.action?.name }.distinct()
        if (actions.isEmpty()) {
            return EvaluationsResponse(evaluations = evaluations.map { EvaluationResponse(decision = false) })
        }

        // Build Cerbos CheckResources request
        val cerbosRequest = buildMap<String, Any?> {
            put("principal", buildMap {
                put("id", subject?.id ?: "")
                put("roles", listOf("_"))
                put("attr", subject?.properties ?: emptyMap<String, Any?>())
            })
            put("resources", listOf(buildMap {
                put("resource", buildMap {
                    put("kind", resource?.type ?: "")
                    put("id", resource?.id ?: "default")
                    put("attr", resource?.properties ?: emptyMap<String, Any?>())
                })
                put("actions", actions)
            }))
        }

        val actionDecisions = callCerbos(cerbosRequest)

        return EvaluationsResponse(
            evaluations = evaluations.map { eval ->
                EvaluationResponse(decision = actionDecisions[eval.action?.name] ?: false)
            }
        )
    }

    override fun evaluation(request: EvaluationRequest): EvaluationResponse {
        val result = evaluations(EvaluationsRequest(
            subject = request.subject,
            resource = request.resource,
            evaluations = listOf(EvaluationRequest(action = request.action))
        ))
        return result.evaluations.firstOrNull() ?: EvaluationResponse(decision = false)
    }

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse =
        throw UnsupportedOperationException("Use evaluations() instead")

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse =
        throw UnsupportedOperationException("Not supported")

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse =
        throw UnsupportedOperationException("Not supported")

    @Suppress("UNCHECKED_CAST")
    private fun callCerbos(cerbosRequest: Map<String, Any?>): Map<String, Boolean> {
        val body = objectMapper.writeValueAsString(cerbosRequest)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/check/resources")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                LOG.warning("Cerbos check failed: ${response.code} ${response.message}")
                return emptyMap()
            }
            val result = objectMapper.readValue<Map<String, Any?>>(response.body?.string() ?: "{}")
            val results = result["results"] as? List<Map<String, Any?>> ?: return emptyMap()

            buildMap {
                for (res in results) {
                    val actions = res["actions"] as? Map<String, Any?> ?: continue
                    for ((actionName, effectInfo) in actions) {
                        val allowed = when (effectInfo) {
                            is Map<*, *> -> effectInfo["effect"] == "EFFECT_ALLOW"
                            is String -> effectInfo == "EFFECT_ALLOW"
                            else -> false
                        }
                        put(actionName, allowed)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "Cerbos check failed", e)
            emptyMap()
        }
    }

    companion object {
        private val LOG = Logger.getLogger(CerbosAccessService::class.java.name)
    }
}
