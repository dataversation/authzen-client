/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.interceptor.InterceptorBinding

/**
 * Marks a method or class for AuthZEN policy enforcement.
 *
 * The [EvaluationRequest][com.dataversation.authzen.model.EvaluationRequest] fields
 * (subject, action, resource, context) are resolved from [@Subject], [@Action],
 * [@Resource], and [@Context] annotations on the method and its parameters,
 * augmented by optional [Provider][SubjectProvider] CDI beans.
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Policy
