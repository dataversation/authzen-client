/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.http

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.dataversation.authzen.AccessService
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.logging.Level
import java.util.logging.Logger

/**
 * AuthZEN AccessService implementation using HTTP transport (OkHttp).
 * Framework-agnostic — no CDI, no MicroProfile, no Spring dependencies.
 *
 * On first use, attempts to discover endpoint URLs from the PDP's
 * `/.well-known/authzen-configuration` metadata endpoint. Falls back to
 * default paths if metadata is unavailable.
 *
 * @param baseUrl Base URL of the AuthZEN PDP (e.g. "https://localhost:9393")
 * @param httpClient Optional pre-configured OkHttpClient instance
 */
class AuthZenHttpAccessService(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : AccessService {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Lazily discovered endpoint configuration from `/.well-known/authzen-configuration`.
     * Falls back to default paths if metadata is unavailable.
     */
    private val endpoints: AuthZenEndpoints by lazy { discoverEndpoints() }

    override fun evaluation(request: EvaluationRequest): EvaluationResponse =
        post(endpoints.evaluationUrl, request)

    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse =
        post(endpoints.evaluationsUrl, request)

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse =
        post(endpoints.searchActionUrl, request)

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse =
        post(endpoints.searchSubjectUrl, request)

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse =
        post(endpoints.searchResourceUrl, request)

    private fun discoverEndpoints(): AuthZenEndpoints {
        val metadataUrl = "${baseUrl.trimEnd('/')}/.well-known/authzen-configuration"
        return try {
            val request = Request.Builder().url(metadataUrl).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val metadata = objectMapper.readValue<AuthZenMetadata>(body)
                    LOG.info("Discovered AuthZEN PDP endpoints from $metadataUrl")
                    AuthZenEndpoints(
                        evaluationUrl = rebaseUrl(metadata.accessEvaluationEndpoint)
                            ?: defaultUrl("access/v1/evaluation"),
                        evaluationsUrl = rebaseUrl(metadata.accessEvaluationsEndpoint)
                            ?: defaultUrl("access/v1/evaluations"),
                        searchActionUrl = rebaseUrl(metadata.searchActionEndpoint)
                            ?: defaultUrl("access/v1/search/action"),
                        searchSubjectUrl = rebaseUrl(metadata.searchSubjectEndpoint)
                            ?: defaultUrl("access/v1/search/subject"),
                        searchResourceUrl = rebaseUrl(metadata.searchResourceEndpoint)
                            ?: defaultUrl("access/v1/search/resource")
                    )
                } else {
                    LOG.info("AuthZEN metadata response empty, using default endpoints")
                    defaultEndpoints()
                }
            } else {
                LOG.info(
                    "AuthZEN metadata not available at $metadataUrl " +
                        "(${response.code}), using default endpoints"
                )
                defaultEndpoints()
            }
        } catch (e: Exception) {
            LOG.log(Level.INFO, "Failed to fetch AuthZEN metadata from $metadataUrl, using default endpoints", e)
            defaultEndpoints()
        }
    }

    /**
     * Rebase a discovered absolute URL onto the configured [baseUrl].
     * PDPs may advertise endpoints with their internal hostname (e.g. localhost:9393),
     * but the client should always use the configured baseUrl for connectivity.
     * Extracts just the path component and appends it to baseUrl.
     */
    private fun rebaseUrl(discoveredUrl: String?): String? {
        discoveredUrl ?: return null
        return try {
            val path = java.net.URI(discoveredUrl).path
            "${baseUrl.trimEnd('/')}$path"
        } catch (_: Exception) {
            discoveredUrl
        }
    }

    private fun defaultUrl(path: String) = "${baseUrl.trimEnd('/')}/$path"

    private fun defaultEndpoints() = AuthZenEndpoints(
        evaluationUrl = defaultUrl("access/v1/evaluation"),
        evaluationsUrl = defaultUrl("access/v1/evaluations"),
        searchActionUrl = defaultUrl("access/v1/search/action"),
        searchSubjectUrl = defaultUrl("access/v1/search/subject"),
        searchResourceUrl = defaultUrl("access/v1/search/resource")
    )

    private inline fun <reified T> post(url: String, body: Any): T {
        val jsonBody = objectMapper.writeValueAsString(body)
        LOG.fine { "AuthZEN request to $url: $jsonBody" }
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val responseBody = response.body?.string()
            throw AuthZenHttpException(
                statusCode = response.code,
                message = "AuthZEN PDP request to $url failed: ${response.code} ${response.message}" +
                    "\n  Request:  $jsonBody" +
                    "\n  Response: $responseBody",
                responseBody = responseBody
            )
        }
        return objectMapper.readValue(
            response.body?.string() ?: throw AuthZenHttpException(
                statusCode = response.code,
                message = "AuthZEN PDP returned empty response body for $url"
            )
        )
    }

    companion object {
        private val LOG = Logger.getLogger(AuthZenHttpAccessService::class.java.name)
    }
}

/**
 * Resolved endpoint URLs for the AuthZEN PDP.
 */
private data class AuthZenEndpoints(
    val evaluationUrl: String,
    val evaluationsUrl: String,
    val searchActionUrl: String,
    val searchSubjectUrl: String,
    val searchResourceUrl: String
)

/**
 * Metadata response from `/.well-known/authzen-configuration`.
 */
private data class AuthZenMetadata(
    val policyDecisionPoint: String? = null,
    val accessEvaluationEndpoint: String? = null,
    val accessEvaluationsEndpoint: String? = null,
    val searchSubjectEndpoint: String? = null,
    val searchResourceEndpoint: String? = null,
    val searchActionEndpoint: String? = null
)

/**
 * Exception thrown when an AuthZEN PDP HTTP request fails.
 */
class AuthZenHttpException(
    val statusCode: Int,
    override val message: String,
    val responseBody: String? = null
) : RuntimeException(message)
