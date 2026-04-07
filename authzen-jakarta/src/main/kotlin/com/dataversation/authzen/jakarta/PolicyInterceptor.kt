/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import com.dataversation.authzen.AccessService
import com.dataversation.authzen.model.Action as AuthZenAction
import com.dataversation.authzen.model.EvaluationRequest
import com.dataversation.authzen.model.Resource as AuthZenResource
import com.dataversation.authzen.model.Subject as AuthZenSubject
import jakarta.annotation.Priority
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InvocationContext

/**
 * CDI interceptor that enforces AuthZEN policy decisions.
 *
 * Builds an [EvaluationRequest] from [@Subject], [@Action], [@Resource], and [@Context]
 * annotations (on the method and its parameters), augmented by optional provider CDI beans.
 * Calls [AccessService.evaluation] and throws [PolicyException] if denied.
 */
@Policy
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
class PolicyInterceptor @Inject constructor(
    private val accessService: AccessService,
    private val converterRegistry: ConverterRegistry,
    private val subjectProviderInstance: Instance<SubjectProvider>,
    private val actionProviderInstance: Instance<ActionProvider>,
    private val resourceProviderInstance: Instance<ResourceProvider>,
    private val contextProviderInstance: Instance<ContextProvider>
) {
    @AroundInvoke
    fun enforce(ctx: InvocationContext): Any? {
        val method = ctx.method
        val parameters = method.parameters
        val args = ctx.parameters

        var subject = resolveSubject(method, parameters, args)
        var action = resolveAction(method, parameters, args)
        var resource = resolveResource(method, parameters, args)
        var context = resolveContext(parameters, args)

        // Let providers augment/fill the fields
        if (!subjectProviderInstance.isUnsatisfied) {
            subject = subjectProviderInstance.get().provide(ctx, subject)
        }
        if (!actionProviderInstance.isUnsatisfied) {
            action = actionProviderInstance.get().provide(ctx, action)
        }
        if (!resourceProviderInstance.isUnsatisfied) {
            resource = resourceProviderInstance.get().provide(ctx, resource)
        }
        if (!contextProviderInstance.isUnsatisfied) {
            context = contextProviderInstance.get().provide(ctx, context)
        }

        val request = EvaluationRequest(
            subject = subject,
            action = action,
            resource = resource,
            context = context
        )
        val response = accessService.evaluation(request)
        if (!response.decision) {
            throw PolicyException(
                "Access denied: action='${action?.name}', resource.type='${resource?.type}'"
            )
        }

        return ctx.proceed()
    }

    private fun resolveSubject(
        method: java.lang.reflect.Method,
        parameters: Array<java.lang.reflect.Parameter>,
        args: Array<Any?>
    ): AuthZenSubject? {
        val methodAnnotation = method.getAnnotation(Subject::class.java)
        var subject: AuthZenSubject? = null
        val properties = mutableMapOf<String, Any?>()

        // Check parameters
        for (i in parameters.indices) {
            val ann = parameters[i].getAnnotation(Subject::class.java) ?: continue
            val value = args[i]
            if (ann.property.isEmpty()) {
                // Parameter IS the subject
                subject = convertOrCast<AuthZenSubject, SubjectConverter<Any>>(
                    value, parameters[i].type
                ) { type -> converterRegistry.findSubjectConverter(type) }
            } else {
                properties[ann.property] = value
            }
        }

        // Apply method-level defaults
        if (methodAnnotation != null) {
            subject = (subject ?: AuthZenSubject()).let {
                it.copy(
                    type = it.type.ifEmpty { methodAnnotation.type },
                    properties = mergeProperties(it.properties, properties)
                )
            }
        } else if (properties.isNotEmpty()) {
            subject = (subject ?: AuthZenSubject()).copy(
                properties = mergeProperties(subject?.properties, properties)
            )
        }

        return subject
    }

    private fun resolveAction(
        method: java.lang.reflect.Method,
        parameters: Array<java.lang.reflect.Parameter>,
        args: Array<Any?>
    ): AuthZenAction? {
        val methodAnnotation = method.getAnnotation(Action::class.java)
        var action: AuthZenAction? = null
        val properties = mutableMapOf<String, Any?>()

        for (i in parameters.indices) {
            val ann = parameters[i].getAnnotation(Action::class.java) ?: continue
            val value = args[i]
            if (ann.property.isEmpty()) {
                action = convertOrCast<AuthZenAction, ActionConverter<Any>>(
                    value, parameters[i].type
                ) { type -> converterRegistry.findActionConverter(type) }
            } else {
                properties[ann.property] = value
            }
        }

        if (methodAnnotation != null) {
            action = (action ?: AuthZenAction()).let {
                it.copy(
                    name = it.name.ifEmpty { methodAnnotation.name },
                    properties = mergeProperties(it.properties, properties)
                )
            }
        } else if (properties.isNotEmpty()) {
            action = (action ?: AuthZenAction()).copy(
                properties = mergeProperties(action?.properties, properties)
            )
        }

        return action
    }

    private fun resolveResource(
        method: java.lang.reflect.Method,
        parameters: Array<java.lang.reflect.Parameter>,
        args: Array<Any?>
    ): AuthZenResource? {
        val methodAnnotation = method.getAnnotation(Resource::class.java)
        var resource: AuthZenResource? = null
        val properties = mutableMapOf<String, Any?>()

        for (i in parameters.indices) {
            val ann = parameters[i].getAnnotation(Resource::class.java) ?: continue
            val value = args[i]
            if (ann.property.isEmpty()) {
                resource = convertOrCast<AuthZenResource, ResourceConverter<Any>>(
                    value, parameters[i].type
                ) { type -> converterRegistry.findResourceConverter(type) }
            } else {
                properties[ann.property] = value
            }
        }

        if (methodAnnotation != null) {
            resource = (resource ?: AuthZenResource()).let {
                it.copy(
                    type = it.type.ifEmpty { methodAnnotation.type },
                    properties = mergeProperties(it.properties, properties)
                )
            }
        } else if (properties.isNotEmpty()) {
            resource = (resource ?: AuthZenResource()).copy(
                properties = mergeProperties(resource?.properties, properties)
            )
        }

        return resource
    }

    private fun resolveContext(
        parameters: Array<java.lang.reflect.Parameter>,
        args: Array<Any?>
    ): Map<String, Any?>? {
        val properties = mutableMapOf<String, Any?>()
        var contextMap: Map<String, Any?>? = null

        for (i in parameters.indices) {
            val ann = parameters[i].getAnnotation(Context::class.java) ?: continue
            val value = args[i]
            if (ann.property.isEmpty()) {
                // Parameter IS the context
                contextMap = when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        value as Map<String, Any?>
                    }
                    null -> null
                    else -> {
                        val converter = converterRegistry.findContextConverter(
                            @Suppress("UNCHECKED_CAST")
                            (value::class.java as Class<Any>)
                        ) ?: throw IllegalStateException(
                            "No ContextConverter for ${value::class.java.name}"
                        )
                        @Suppress("UNCHECKED_CAST")
                        (converter as ContextConverter<Any>).convert(value)
                    }
                }
            } else {
                properties[ann.property] = value
            }
        }

        return if (properties.isNotEmpty()) {
            mergeProperties(contextMap, properties)
        } else {
            contextMap
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified M, C> convertOrCast(
        value: Any?,
        paramType: Class<*>,
        findConverter: (Class<Any>) -> Any?
    ): M? {
        if (value == null) return null
        if (value is M) return value

        val converter = findConverter(paramType as Class<Any>)
            ?: throw IllegalStateException(
                "No converter registered for ${paramType.name} " +
                    "and it is not a ${M::class.simpleName}"
            )

        return when (converter) {
            is SubjectConverter<*> -> (converter as SubjectConverter<Any>).convert(value) as M
            is ActionConverter<*> -> (converter as ActionConverter<Any>).convert(value) as M
            is ResourceConverter<*> -> (converter as ResourceConverter<Any>).convert(value) as M
            else -> throw IllegalStateException("Unknown converter type")
        }
    }

    private fun mergeProperties(
        existing: Map<String, Any?>?,
        additions: Map<String, Any?>
    ): Map<String, Any?>? {
        if (additions.isEmpty()) return existing
        return buildMap {
            existing?.let { putAll(it) }
            putAll(additions)
        }
    }
}
