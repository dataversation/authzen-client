/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.topaz

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
 * AuthZEN AccessService implementation for [Topaz](https://www.topaz.sh/).
 *
 * Uses the Topaz **`is` API** (`/api/v2/authz/is`) with multiple decisions to evaluate
 * all actions in a single HTTP call. Each action name maps to a Rego decision rule
 * (e.g. `view`, `edit`, `delete`).
 *
 * The AuthZEN subject and resource are passed via `resourceContext` so the Rego policies
 * can access them as `input.resource_context.subject.properties.*` etc.
 *
 * @param baseUrl Topaz gateway URL (e.g. "https://topaz:8383")
 * @param regoPackagePrefix Rego package prefix (e.g. "myapp"); the policy path for a resource type
 *   is derived as `"$regoPackagePrefix.$resourceType"` (after applying [resourceTypeMap] overrides).
 * @param httpClient Pre-configured OkHttpClient (use trust-all for self-signed certs)
 * @param resourceTypeMap Optional overrides from AuthZEN resource type to Rego policy name
 *   (e.g. `"myResource" to "my_resource"`). Resource types not in this map are used as-is.
 */
class TopazAccessService @JvmOverloads constructor(
    private val baseUrl: String,
    private val regoPackagePrefix: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val resourceTypeMap: Map<String, String> = emptyMap()
) : AccessService {

    private val objectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Batch evaluation using a single Topaz `is` API call with multiple decisions.
     */
    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
        // This adapter translates batched action names into a single Topaz `is` call.
        // Per-evaluation overrides are not supported.
        for (eval in request.evaluations) {
            if (eval.subject != null) throw UnsupportedOperationException(
                "Per-evaluation subject overrides are not supported by the Topaz adapter"
            )
            if (eval.resource != null) throw UnsupportedOperationException(
                "Per-evaluation resource overrides are not supported by the Topaz adapter"
            )
            if (!eval.action?.properties.isNullOrEmpty()) throw UnsupportedOperationException(
                "Action properties are not supported by the Topaz adapter"
            )
        }

        val actions = request.evaluations.mapNotNull { it.action?.name }
        if (actions.isEmpty()) {
            return EvaluationsResponse(evaluations = request.evaluations.map { EvaluationResponse(decision = false) })
        }

        val policyPath = resolvePolicyPath(request.resource?.type ?: "")
        val resourceContext = buildResourceContext(request.subject, request.resource)
        val decisions = queryTopazIs(policyPath, actions, resourceContext)

        return EvaluationsResponse(
            evaluations = request.evaluations.map { eval ->
                EvaluationResponse(decision = decisions[eval.action?.name] ?: false)
            }
        )
    }

    override fun evaluation(request: EvaluationRequest): EvaluationResponse {
        val policyPath = resolvePolicyPath(request.resource?.type ?: "")
        val actionName = request.action?.name ?: ""
        val resourceContext = buildResourceContext(request.subject, request.resource)
        val decisions = queryTopazIs(policyPath, listOf(actionName), resourceContext)
        return EvaluationResponse(decision = decisions[actionName] ?: false)
    }

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse =
        throw UnsupportedOperationException("Use evaluations() instead")

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse =
        throw UnsupportedOperationException("Not supported")

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse =
        throw UnsupportedOperationException("Not supported")

    /**
     * Resolve the Rego policy path for a given AuthZEN resource type.
     * Applies [resourceTypeMap] overrides, then prefixes with [regoPackagePrefix].
     */
    private fun resolvePolicyPath(resourceType: String): String {
        val mappedType = resourceTypeMap.getOrDefault(resourceType, resourceType)
        return "$regoPackagePrefix.$mappedType"
    }

    /**
     * Build the resourceContext that passes AuthZEN subject/resource to the Rego policy.
     * Rego accesses these as `input.resource_context.subject.properties.roles` etc.
     */
    private fun buildResourceContext(subject: Subject?, resource: Resource?): Map<String, Any?> = buildMap {
        subject?.let {
            put("subject", buildMap<String, Any?> {
                put("type", it.type)
                put("id", it.id)
                it.properties?.let { props -> put("properties", props) }
            })
        }
        resource?.let {
            put("resource", buildMap<String, Any?> {
                put("type", it.type)
                it.id?.let { id -> put("id", id) }
                it.properties?.let { props -> put("properties", props) }
            })
        }
    }

    /**
     * Call Topaz `is` API with multiple decisions in one HTTP request.
     * Returns a map of decision name → boolean result.
     */
    @Suppress("UNCHECKED_CAST")
    private fun queryTopazIs(
        policyPath: String, decisions: List<String>, resourceContext: Map<String, Any?>
    ): Map<String, Boolean> {
        val body = objectMapper.writeValueAsString(mapOf(
            "identity_context" to mapOf("type" to "IDENTITY_TYPE_NONE"),
            "policy_context" to mapOf("path" to policyPath, "decisions" to decisions),
            "resource_context" to resourceContext
        ))

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v2/authz/is")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                LOG.warning("Topaz is API failed: ${response.code} ${response.message}")
                return emptyMap()
            }
            val result = objectMapper.readValue<Map<String, Any?>>(response.body?.string() ?: "{}")
            // Response: {"decisions": [{"decision": "view", "is": true}, ...]}
            val decisionList = result["decisions"] as? List<Map<String, Any?>> ?: return emptyMap()
            decisionList.associate { d ->
                (d["decision"] as? String ?: "") to (d["is"] == true)
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "Topaz is API failed", e)
            emptyMap()
        }
    }

    companion object {
        private val LOG = Logger.getLogger(TopazAccessService::class.java.name)
    }
}
