/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.enterprise.util.Nonbinding

/**
 * Specifies context for an AuthZEN evaluation request.
 *
 * **Parameter-level (no [property]):** the parameter IS the context — must be a
 * `Map<String, Any?>` or resolved via a [ContextConverter].
 *
 * **Parameter-level (with [property]):** the parameter value is serialized into
 * `context[property]`.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Context(
    @get:Nonbinding val property: String = ""
)
