/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import com.dataversation.authzen.model.Action as AuthZenAction

/**
 * Converts a domain object to an AuthZEN [Action][AuthZenAction].
 *
 * Register implementations as CDI beans; they are discovered automatically
 * by the [ConverterRegistry].
 */
interface ActionConverter<T : Any> {
    val domainType: Class<T>
    fun convert(domainObject: T): AuthZenAction
}
