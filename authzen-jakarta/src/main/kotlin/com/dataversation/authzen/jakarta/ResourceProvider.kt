/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import com.dataversation.authzen.model.Resource as AuthZenResource
import jakarta.interceptor.InvocationContext

/**
 * Provides or augments the [Resource][AuthZenResource] for an AuthZEN evaluation.
 */
interface ResourceProvider {
    fun provide(context: InvocationContext, current: AuthZenResource?): AuthZenResource
}
