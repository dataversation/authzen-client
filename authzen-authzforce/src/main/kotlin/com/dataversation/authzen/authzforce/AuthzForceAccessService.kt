/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.authzforce

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
 * AuthZEN AccessService implementation for [AuthzForce CE](https://github.com/authzforce/server).
 *
 * AuthzForce is a XACML 3.0 PDP. This adapter translates AuthZEN Evaluations API requests
 * into XACML JSON Profile requests using the **Multiple Decision Profile (MDP)** —
 * all actions are sent in a single XACML request with `CombinedDecision=false`,
 * producing one Decision per action.
 *
 * AuthZEN attributes are generically mapped to XACML categories:
 * - subject.id → AccessSubject.subject-id
 * - subject.properties.* → AccessSubject.* (collections become multi-valued string bags)
 * - action.name → Action.action-id (one per repeated Action category in MDP)
 * - resource.type → Resource.resource-type
 * - resource.properties.* → Resource.* (collections become multi-valued string bags)
 *
 * @param baseUrl AuthzForce base URL (e.g. "http://authzforce:8080/authzforce-ce").
 *   The PDP domain is auto-discovered from the first available domain.
 * @param httpClient Pre-configured OkHttpClient
 */
class AuthzForceAccessService @JvmOverloads constructor(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : AccessService {

    private val objectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val xacmlJsonMediaType = "application/xacml+json".toMediaType()

    /** Lazily discover the PDP URL by finding the first available domain. */
    private val pdpUrl: String by lazy { discoverPdpUrl() }

    private fun discoverPdpUrl(): String {
        val domainsUrl = "${baseUrl.trimEnd('/')}/domains"
        val request = Request.Builder().url(domainsUrl).header("Accept", "application/xml").get().build()
        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            // Parse domain ID from XML: <link ... href="domainId" .../>
            val domainId = Regex("href=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: throw IllegalStateException("No domains found at $domainsUrl")
            val url = "${baseUrl.trimEnd('/')}/domains/$domainId/pdp"
            LOG.info("Discovered AuthzForce PDP at $url")
            url
        } catch (e: Exception) {
            LOG.log(Level.SEVERE, "Failed to discover AuthzForce domain at $domainsUrl", e)
            throw IllegalStateException("Failed to discover AuthzForce domain", e)
        }
    }

    /**
     * Batch evaluation using XACML Multiple Decision Profile (MDP).
     * All actions are sent in a single XACML request with repeated Action categories.
     * The `repeated-attribute-categories-lax` preprocessor creates one sub-request per action,
     * returning one Decision per action in the response.
     */
    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
        // This adapter translates batched action names into XACML MDP requests.
        // Per-evaluation overrides are not supported.
        for (eval in request.evaluations) {
            if (eval.subject != null) throw UnsupportedOperationException(
                "Per-evaluation subject overrides are not supported by the AuthzForce adapter"
            )
            if (eval.resource != null) throw UnsupportedOperationException(
                "Per-evaluation resource overrides are not supported by the AuthzForce adapter"
            )
            if (!eval.action?.properties.isNullOrEmpty()) throw UnsupportedOperationException(
                "Action properties are not supported by the AuthzForce adapter"
            )
        }

        val actions = request.evaluations.mapNotNull { it.action?.name }
        if (actions.isEmpty()) {
            return EvaluationsResponse(evaluations = request.evaluations.map { EvaluationResponse(decision = false) })
        }

        val xacmlRequest = buildMdpRequest(request.subject, request.resource, actions)
        val decisions = callPdpMdp(xacmlRequest, actions.size)

        return EvaluationsResponse(evaluations = decisions.map { EvaluationResponse(decision = it) })
    }

    override fun evaluation(request: EvaluationRequest): EvaluationResponse {
        val xacmlRequest = buildSingleRequest(request.subject, request.resource, request.action?.name ?: "")
        return EvaluationResponse(decision = callPdpSingle(xacmlRequest))
    }

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse =
        throw UnsupportedOperationException("Use evaluations() instead")

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse =
        throw UnsupportedOperationException("Not supported")

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse =
        throw UnsupportedOperationException("Not supported")

    /**
     * Build an MDP request with repeated Action categories.
     * The `repeated-attribute-categories-lax` preprocessor creates one sub-request per Action.
     */
    private fun buildMdpRequest(subject: Subject?, resource: Resource?, actions: List<String>): Map<String, Any?> {
        val categories = mutableListOf<Map<String, Any?>>()
        categories.addAll(buildSubjectAndResourceCategories(subject, resource))
        // One Action category per action — the MDP preprocessor handles the cartesian product
        for (actionName in actions) {
            categories.add(mapOf(
                "CategoryId" to ACTION_CATEGORY,
                "Attribute" to listOf(mapOf(
                    "AttributeId" to "urn:oasis:names:tc:xacml:1.0:action:action-id",
                    "DataType" to XSD_STRING, "Value" to actionName
                ))
            ))
        }
        return mapOf("Request" to mapOf("Category" to categories))
    }

    private fun buildSingleRequest(subject: Subject?, resource: Resource?, actionName: String): Map<String, Any?> {
        val categories = mutableListOf<Map<String, Any?>>()
        categories.addAll(buildSubjectAndResourceCategories(subject, resource))
        categories.add(mapOf(
            "CategoryId" to ACTION_CATEGORY,
            "Attribute" to listOf(mapOf(
                "AttributeId" to "urn:oasis:names:tc:xacml:1.0:action:action-id",
                "DataType" to XSD_STRING, "Value" to actionName
            ))
        ))
        return mapOf("Request" to mapOf("Category" to categories))
    }

    private fun buildSubjectAndResourceCategories(subject: Subject?, resource: Resource?): List<Map<String, Any?>> {
        val categories = mutableListOf<Map<String, Any?>>()
        val subjectAttrs = mutableListOf<Map<String, Any?>>()
        subject?.id?.let {
            subjectAttrs.add(xacmlAttr("urn:oasis:names:tc:xacml:1.0:subject:subject-id", it))
        }
        subject?.properties?.forEach { (key, value) ->
            if (value != null) subjectAttrs.addAll(propertyToXacmlAttrs(key, value))
        }
        if (subjectAttrs.isNotEmpty()) {
            categories.add(mapOf("CategoryId" to SUBJECT_CATEGORY, "Attribute" to subjectAttrs))
        }
        val resourceAttrs = mutableListOf<Map<String, Any?>>()
        resource?.type?.let {
            resourceAttrs.add(xacmlAttr("resource-type", it))
        }
        resource?.properties?.forEach { (key, value) ->
            if (value != null) resourceAttrs.addAll(propertyToXacmlAttrs(key, value))
        }
        if (resourceAttrs.isNotEmpty()) {
            categories.add(mapOf("CategoryId" to RESOURCE_CATEGORY, "Attribute" to resourceAttrs))
        }
        return categories
    }

    /**
     * Convert a property value to XACML Attribute entries.
     * Collections produce one entry per element (forming a multi-valued XACML bag).
     */
    private fun propertyToXacmlAttrs(key: String, value: Any): List<Map<String, Any?>> =
        when (value) {
            is Collection<*> -> value.filterNotNull().map { xacmlAttr(key, it.toString()) }
            else -> listOf(xacmlAttr(key, value.toString()))
        }

    private fun xacmlAttr(id: String, value: String) =
        mapOf("AttributeId" to id, "DataType" to XSD_STRING, "Value" to value)

    @Suppress("UNCHECKED_CAST")
    private fun callPdpMdp(xacmlRequest: Map<String, Any?>, expectedCount: Int): List<Boolean> {
        val body = objectMapper.writeValueAsString(xacmlRequest)
        val request = Request.Builder()
            .url(pdpUrl).post(body.toRequestBody(xacmlJsonMediaType))
            .header("Accept", "application/xacml+json").build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                LOG.warning("AuthzForce MDP failed: ${response.code} ${response.body?.string()?.take(200)}")
                return List(expectedCount) { false }
            }
            val result = objectMapper.readValue<Map<String, Any?>>(response.body?.string() ?: "{}")
            val responseList = result["Response"] as? List<Map<String, Any?>> ?: return List(expectedCount) { false }
            responseList.map { it["Decision"] == "Permit" }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "AuthzForce MDP failed", e)
            List(expectedCount) { false }
        }
    }

    private fun callPdpSingle(xacmlRequest: Map<String, Any?>): Boolean {
        val body = objectMapper.writeValueAsString(xacmlRequest)
        val request = Request.Builder()
            .url(pdpUrl).post(body.toRequestBody(xacmlJsonMediaType))
            .header("Accept", "application/xacml+json").build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return false
            val result = objectMapper.readValue<Map<String, Any?>>(response.body?.string() ?: "{}")
            extractDecision(result)
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "AuthzForce PDP failed", e)
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractDecision(response: Map<String, Any?>): Boolean {
        val responseList = response["Response"] as? List<Map<String, Any?>> ?: return false
        if (responseList.isEmpty()) return false
        return responseList[0]["Decision"] == "Permit"
    }

    companion object {
        private val LOG = Logger.getLogger(AuthzForceAccessService::class.java.name)
        private const val SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject"
        private const val ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action"
        private const val RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource"
        private const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
    }
}
