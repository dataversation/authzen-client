/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import com.dataversation.authzen.model.Resource as AuthZenResource

/**
 * Converts a domain object to an AuthZEN [Resource][AuthZenResource].
 *
 * Register implementations as CDI beans; they are discovered automatically
 * by the [ConverterRegistry].
 */
interface ResourceConverter<T : Any> {
    val domainType: Class<T>
    fun convert(domainObject: T): AuthZenResource
}
