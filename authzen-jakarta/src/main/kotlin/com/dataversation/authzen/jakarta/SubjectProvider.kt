/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import com.dataversation.authzen.model.Subject as AuthZenSubject
import jakarta.interceptor.InvocationContext

/**
 * Provides or augments the [Subject][AuthZenSubject] for an AuthZEN evaluation.
 *
 * Receives the full [InvocationContext] so it can inspect method annotations and
 * parameters to determine the subject. Commonly used to resolve the current
 * authenticated user from a request-scoped CDI bean.
 *
 * @param context the CDI invocation context
 * @param current the subject built so far from annotations (null if none)
 */
interface SubjectProvider {
    fun provide(context: InvocationContext, current: AuthZenSubject?): AuthZenSubject
}
