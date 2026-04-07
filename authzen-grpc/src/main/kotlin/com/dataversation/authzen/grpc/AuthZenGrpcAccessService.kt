/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.grpc

import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import javax.net.ssl.SSLSocketFactory
import com.dataversation.authzen.AccessService
import com.dataversation.authzen.grpc.generated.AccessGrpc
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
import com.dataversation.authzen.grpc.generated.ActionSearchRequest as GrpcActionSearchRequest
import com.dataversation.authzen.grpc.generated.EvaluationRequest as GrpcEvaluationRequest
import com.dataversation.authzen.grpc.generated.EvaluationsRequest as GrpcEvaluationsRequest
import com.dataversation.authzen.grpc.generated.ResourceSearchRequest as GrpcResourceSearchRequest
import com.dataversation.authzen.grpc.generated.SubjectSearchRequest as GrpcSubjectSearchRequest
import com.dataversation.authzen.model.Action as ModelAction
import com.dataversation.authzen.model.Resource as ModelResource
import com.dataversation.authzen.model.Subject as ModelSubject

/**
 * AuthZEN AccessService implementation using gRPC transport.
 * Framework-agnostic — no CDI, no MicroProfile, no Spring dependencies.
 *
 * @param target gRPC target (e.g. "localhost:9292")
 * @param useTls whether to use TLS (default: false for local development)
 */
class AuthZenGrpcAccessService private constructor(
    private val channel: ManagedChannel
) : AccessService {

    /**
     * Create with a target string.
     * @param target gRPC target (e.g. "localhost:9292")
     * @param useTls whether to use TLS (default: false)
     * @param sslSocketFactory optional custom SSL socket factory (e.g. for self-signed certs)
     */
    /**
     * Create with a target string.
     * @param target gRPC target (e.g. "localhost:9292")
     * @param useTls whether to use TLS (default: false)
     * @param sslSocketFactory optional custom SSL socket factory (e.g. for self-signed certs).
     *   When provided with useTls=true, hostname verification is disabled to support
     *   self-signed certificates where the CN/SAN may not match the target hostname.
     */
    constructor(
        target: String,
        useTls: Boolean = false,
        sslSocketFactory: SSLSocketFactory? = null
    ) : this(
        when {
            useTls && sslSocketFactory != null -> {
                val parts = target.split(":")
                OkHttpChannelBuilder.forAddress(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 443)
                    .useTransportSecurity()
                    .sslSocketFactory(sslSocketFactory)
                    .hostnameVerifier { _, _ -> true }
                    .build()
            }
            useTls -> ManagedChannelBuilder.forTarget(target).build()
            else -> ManagedChannelBuilder.forTarget(target).usePlaintext().build()
        }
    )

    private val stub = AccessGrpc.newBlockingStub(channel)

    override fun evaluation(request: EvaluationRequest): EvaluationResponse {
        val grpcRequest = GrpcEvaluationRequest.newBuilder().apply {
            request.subject?.let { subject = it.toProto() }
            request.action?.let { action = it.toProto() }
            request.resource?.let { resource = it.toProto() }
            request.context?.let { context = it.toProtoStruct() }
        }.build()
        val grpcResponse = stub.evaluation(grpcRequest)
        return EvaluationResponse(
            decision = grpcResponse.decision,
            context = grpcResponse.context?.toModelMap()
        )
    }

    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
        val grpcRequest = GrpcEvaluationsRequest.newBuilder().apply {
            request.subject?.let { subject = it.toProto() }
            request.action?.let { action = it.toProto() }
            request.resource?.let { resource = it.toProto() }
            request.context?.let { context = it.toProtoStruct() }
            request.evaluations.forEach { eval ->
                addEvaluations(GrpcEvaluationRequest.newBuilder().apply {
                    eval.subject?.let { subject = it.toProto() }
                    eval.action?.let { action = it.toProto() }
                    eval.resource?.let { resource = it.toProto() }
                    eval.context?.let { context = it.toProtoStruct() }
                }.build())
            }
        }.build()
        val grpcResponse = stub.evaluations(grpcRequest)
        return EvaluationsResponse(
            evaluations = grpcResponse.evaluationsList.map { EvaluationResponse(decision = it.decision) }
        )
    }

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse {
        val grpcRequest = GrpcActionSearchRequest.newBuilder().apply {
            request.subject?.let { subject = it.toProto() }
            request.resource?.let { resource = it.toProto() }
            request.action?.let { action = it.toProto() }
            request.context?.let { context = it.toProtoStruct() }
        }.build()
        val grpcResponse = stub.actionSearch(grpcRequest)
        return ActionSearchResponse(
            results = grpcResponse.resultsList.map { ModelAction(name = it.name) }
        )
    }

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse {
        val grpcRequest = GrpcSubjectSearchRequest.newBuilder().apply {
            request.subject?.let { subject = it.toProto() }
            request.action?.let { action = it.toProto() }
            request.resource?.let { resource = it.toProto() }
            request.context?.let { context = it.toProtoStruct() }
        }.build()
        val grpcResponse = stub.subjectSearch(grpcRequest)
        return SubjectSearchResponse(
            results = grpcResponse.resultsList.map { ModelSubject(type = it.type, id = it.id) }
        )
    }

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse {
        val grpcRequest = GrpcResourceSearchRequest.newBuilder().apply {
            request.subject?.let { subject = it.toProto() }
            request.action?.let { action = it.toProto() }
            request.resource?.let { resource = it.toProto() }
            request.context?.let { context = it.toProtoStruct() }
        }.build()
        val grpcResponse = stub.resourceSearch(grpcRequest)
        return ResourceSearchResponse(
            results = grpcResponse.resultsList.map { ModelResource(type = it.type, id = it.id) }
        )
    }

    fun shutdown() {
        channel.shutdown()
    }
}

// Proto conversion helpers

private fun ModelSubject.toProto(): com.dataversation.authzen.grpc.generated.Subject =
    com.dataversation.authzen.grpc.generated.Subject.newBuilder().apply {
        setType(this@toProto.type)
        setId(this@toProto.id)
        this@toProto.properties?.let { setProperties(it.toProtoStruct()) }
    }.build()

private fun ModelAction.toProto(): com.dataversation.authzen.grpc.generated.Action =
    com.dataversation.authzen.grpc.generated.Action.newBuilder().apply {
        setName(this@toProto.name)
        this@toProto.properties?.let { setProperties(it.toProtoStruct()) }
    }.build()

private fun ModelResource.toProto(): com.dataversation.authzen.grpc.generated.Resource =
    com.dataversation.authzen.grpc.generated.Resource.newBuilder().apply {
        setType(this@toProto.type)
        this@toProto.id?.let { setId(it) }
        this@toProto.properties?.let { setProperties(it.toProtoStruct()) }
    }.build()

private fun Map<String, Any?>.toProtoStruct(): Struct =
    Struct.newBuilder().apply {
        this@toProtoStruct.forEach { (key, value) ->
            putFields(key, value.toProtoValue())
        }
    }.build()

private fun Any?.toProtoValue(): Value = when (this) {
    null -> Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build()
    is String -> Value.newBuilder().setStringValue(this).build()
    is Number -> Value.newBuilder().setNumberValue(this.toDouble()).build()
    is Boolean -> Value.newBuilder().setBoolValue(this).build()
    is Map<*, *> -> Value.newBuilder().setStructValue(
        @Suppress("UNCHECKED_CAST")
        (this as Map<String, Any?>).toProtoStruct()
    ).build()
    is List<*> -> Value.newBuilder().setListValue(
        com.google.protobuf.ListValue.newBuilder().apply {
            this@toProtoValue.forEach { addValues(it.toProtoValue()) }
        }.build()
    ).build()
    else -> Value.newBuilder().setStringValue(this.toString()).build()
}

private fun Struct.toModelMap(): Map<String, Any?> =
    fieldsMap.mapValues { (_, value) -> value.toModelValue() }

private fun Value.toModelValue(): Any? = when (kindCase) {
    Value.KindCase.NULL_VALUE -> null
    Value.KindCase.NUMBER_VALUE -> numberValue
    Value.KindCase.STRING_VALUE -> stringValue
    Value.KindCase.BOOL_VALUE -> boolValue
    Value.KindCase.STRUCT_VALUE -> structValue.toModelMap()
    Value.KindCase.LIST_VALUE -> listValue.valuesList.map { it.toModelValue() }
    else -> null
}
