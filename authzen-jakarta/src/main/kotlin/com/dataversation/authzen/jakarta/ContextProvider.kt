/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.interceptor.InvocationContext

/**
 * Provides or augments the context map for an AuthZEN evaluation.
 */
interface ContextProvider {
    fun provide(context: InvocationContext, current: Map<String, Any?>?): Map<String, Any?>
}
