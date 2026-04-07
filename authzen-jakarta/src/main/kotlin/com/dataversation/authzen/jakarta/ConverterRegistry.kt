/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any as CdiAny
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * CDI bean that discovers and indexes all converter implementations.
 * Looks up converters by domain type, with [Class.isAssignableFrom] fallback
 * for interface hierarchies.
 */
@ApplicationScoped
class ConverterRegistry @Inject constructor(
    @CdiAny subjectConverters: Instance<SubjectConverter<*>>,
    @CdiAny actionConverters: Instance<ActionConverter<*>>,
    @CdiAny resourceConverters: Instance<ResourceConverter<*>>,
    @CdiAny contextConverters: Instance<ContextConverter<*>>
) {
    private val subjects: Map<Class<*>, SubjectConverter<*>> = index(subjectConverters)
    private val actions: Map<Class<*>, ActionConverter<*>> = index(actionConverters)
    private val resources: Map<Class<*>, ResourceConverter<*>> = index(resourceConverters)
    private val contexts: Map<Class<*>, ContextConverter<*>> = index(contextConverters)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findSubjectConverter(type: Class<T>): SubjectConverter<T>? =
        lookup(subjects, type) as SubjectConverter<T>?

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findActionConverter(type: Class<T>): ActionConverter<T>? =
        lookup(actions, type) as ActionConverter<T>?

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findResourceConverter(type: Class<T>): ResourceConverter<T>? =
        lookup(resources, type) as ResourceConverter<T>?

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findContextConverter(type: Class<T>): ContextConverter<T>? =
        lookup(contexts, type) as ContextConverter<T>?

    private fun <C> index(instances: Instance<C>): Map<Class<*>, C>
        where C : Any =
        buildMap { instances.forEach { put(domainTypeOf(it), it) } }

    private fun domainTypeOf(converter: Any): Class<*> = when (converter) {
        is SubjectConverter<*> -> converter.domainType
        is ActionConverter<*> -> converter.domainType
        is ResourceConverter<*> -> converter.domainType
        is ContextConverter<*> -> converter.domainType
        else -> throw IllegalArgumentException("Unknown converter type: ${converter::class}")
    }

    private fun <C> lookup(map: Map<Class<*>, C>, type: Class<*>): C? =
        map[type] ?: map.entries.firstOrNull { it.key.isAssignableFrom(type) }?.value
}
