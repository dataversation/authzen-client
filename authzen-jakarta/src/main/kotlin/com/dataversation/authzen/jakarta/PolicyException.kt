/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

/**
 * Thrown when an AuthZEN policy evaluation denies access.
 */
open class PolicyException(
    message: String = "Policy denied"
) : RuntimeException(message)
