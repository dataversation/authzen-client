/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.spicedb

import com.dataversation.authzen.AccessService
import com.dataversation.authzen.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * AuthZEN AccessService implementation for [SpiceDB](https://authzed.com/spicedb).
 *
 * SpiceDB is a ReBAC (Relationship-Based Access Control) engine. This adapter:
 * 1. Auto-provisions role-resource relationships for new resource IDs (so dynamic
 *    resources work without pre-seeding)
 * 2. Checks permissions via SpiceDB's `/v1/permissions/check` endpoint with full consistency
 * 3. Supports an optional [preCheck] predicate for application-level deny-all logic
 *
 * @param baseUrl SpiceDB HTTP URL (e.g. "http://spicedb:8090")
 * @param token SpiceDB pre-shared key
 * @param httpClient Pre-configured OkHttpClient
 * @param resourceTypeMap Mapping from AuthZEN resource type names to SpiceDB type names
 * @param roleAssignments Map of SpiceDB resource type → (relation → list of (role, optional caveat)).
 *   Used to auto-provision role-resource relationships for new resource IDs.
 * @param preCheck Optional predicate applied to each [EvaluationsRequest] before calling SpiceDB.
 *   If it returns false, all evaluations are denied without calling the PDP.
 */
class SpiceDbAccessService @JvmOverloads constructor(
    private val baseUrl: String,
    private val token: String = "test",
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val resourceTypeMap: Map<String, String> = emptyMap(),
    private val roleAssignments: Map<String, Map<String, List<RoleSpec>>> = emptyMap(),
    private val preCheck: java.util.function.Predicate<EvaluationsRequest>? = null
) : AccessService {

    private val objectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMediaType = "application/json".toMediaType()
    private val provisionedResources = ConcurrentHashMap.newKeySet<String>()

    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
        // This adapter translates batched action names into SpiceDB CheckPermission calls.
        // Per-evaluation overrides are not supported.
        for (eval in request.evaluations) {
            if (eval.subject != null) throw UnsupportedOperationException(
                "Per-evaluation subject overrides are not supported by the SpiceDB adapter"
            )
            if (eval.resource != null) throw UnsupportedOperationException(
                "Per-evaluation resource overrides are not supported by the SpiceDB adapter"
            )
            if (!eval.action?.properties.isNullOrEmpty()) throw UnsupportedOperationException(
                "Action properties are not supported by the SpiceDB adapter"
            )
        }

        if (preCheck != null && !preCheck.test(request)) {
            return EvaluationsResponse(
                evaluations = request.evaluations.map { EvaluationResponse(decision = false) }
            )
        }

        val subject = request.subject
        val resource = request.resource
        val subjectId = subject?.id ?: ""
        val resourceType = resolveResourceType(resource?.type ?: "")
        val resourceId = resource?.id ?: resourceType

        val context = buildContext(resource, subjectId)
        ensureResource(resourceType, resourceId)

        val permissions = request.evaluations.mapNotNull { it.action?.name }
        val decisions = bulkCheckPermissions(resourceType, resourceId, permissions, subjectId, context)

        return EvaluationsResponse(
            evaluations = request.evaluations.map { eval ->
                EvaluationResponse(decision = decisions[eval.action?.name] ?: false)
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

    private fun resolveResourceType(type: String) = resourceTypeMap.getOrDefault(type, type)

    private fun buildContext(resource: Resource?, subjectId: String): Map<String, Any?> {
        val context = (resource?.properties ?: emptyMap()).filterValues { it != null }.toMutableMap()
        if ("caller_id" !in context) context["caller_id"] = subjectId
        return context
    }

    private fun ensureResource(resourceType: String, resourceId: String) {
        val key = "$resourceType:$resourceId"
        if (!provisionedResources.add(key)) return

        val assignments = roleAssignments[resourceType] ?: return
        for ((relation, roles) in assignments) {
            for (roleSpec in roles) {
                writeRelationship(resourceType, resourceId, relation, roleSpec.role, roleSpec.caveat)
            }
        }
    }

    private fun writeRelationship(
        resourceType: String, resourceId: String,
        relation: String, role: String, caveat: String?
    ) {
        val relationship = buildMap<String, Any?> {
            put("resource", mapOf("objectType" to resourceType, "objectId" to resourceId))
            put("relation", relation)
            put("subject", mapOf(
                "object" to mapOf("objectType" to "role", "objectId" to role),
                "optionalRelation" to "member"
            ))
            caveat?.let { put("optionalCaveat", mapOf("caveatName" to it)) }
        }

        val body = objectMapper.writeValueAsString(mapOf(
            "updates" to listOf(mapOf("operation" to "OPERATION_TOUCH", "relationship" to relationship))
        ))

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/relationships/write")
            .post(body.toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warning("SpiceDB write failed: ${response.code} ${response.body?.string()?.take(200)}")
                }
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "SpiceDB write failed", e)
        }
    }

    private fun checkPermission(
        resourceType: String, resourceId: String,
        permission: String, subjectId: String,
        context: Map<String, Any?>
    ): Boolean {
        val body = buildMap<String, Any?> {
            put("consistency", mapOf("fullyConsistent" to true))
            put("resource", mapOf("objectType" to resourceType, "objectId" to resourceId))
            put("permission", permission)
            put("subject", mapOf("object" to mapOf("objectType" to "user", "objectId" to subjectId)))
            if (context.isNotEmpty()) put("context", context)
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/permissions/check")
            .post(objectMapper.writeValueAsString(body).toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val result = objectMapper.readValue<Map<String, Any?>>(response.body?.string() ?: "{}")
                result["permissionship"] == "PERMISSIONSHIP_HAS_PERMISSION"
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "SpiceDB check failed", e)
            false
        }
    }

    /**
     * Bulk check multiple permissions in a single HTTP call using SpiceDB's experimental
     * BulkCheckPermission endpoint. Returns a map of permission name → boolean.
     */
    @Suppress("UNCHECKED_CAST")
    private fun bulkCheckPermissions(
        resourceType: String, resourceId: String,
        permissions: List<String>, subjectId: String,
        context: Map<String, Any?>
    ): Map<String, Boolean> {
        ensureResource(resourceType, resourceId)

        val items = permissions.map { permission ->
            buildMap<String, Any?> {
                put("resource", mapOf("objectType" to resourceType, "objectId" to resourceId))
                put("permission", permission)
                put("subject", mapOf("object" to mapOf("objectType" to "user", "objectId" to subjectId)))
                if (context.isNotEmpty()) put("context", context)
            }
        }

        val body = objectMapper.writeValueAsString(mapOf(
            "consistency" to mapOf("fullyConsistent" to true),
            "items" to items
        ))

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/experimental/permissions/bulkcheckpermission")
            .post(body.toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warning("SpiceDB bulk check failed: ${response.code} ${response.body?.string()?.take(200)}")
                    return permissions.associateWith { false }
                }
                val result = objectMapper.readValue<Map<String, Any?>>(response.body?.string() ?: "{}")
                val pairs = result["pairs"] as? List<Map<String, Any?>> ?: return permissions.associateWith { false }
                pairs.associate { pair ->
                    val req = pair["request"] as? Map<String, Any?> ?: return@associate "" to false
                    val item = pair["item"] as? Map<String, Any?> ?: return@associate "" to false
                    val perm = req["permission"] as? String ?: ""
                    perm to (item["permissionship"] == "PERMISSIONSHIP_HAS_PERMISSION")
                }
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "SpiceDB bulk check failed", e)
            permissions.associateWith { false }
        }
    }

    companion object {
        private val LOG = Logger.getLogger(SpiceDbAccessService::class.java.name)
    }
}

/** A role assignment specification, optionally with a caveat (condition). */
data class RoleSpec(val role: String, val caveat: String? = null)
