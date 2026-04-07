/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

/**
 * Converts a domain object to an AuthZEN context map.
 *
 * Register implementations as CDI beans; they are discovered automatically
 * by the [ConverterRegistry].
 */
interface ContextConverter<T : Any> {
    val domainType: Class<T>
    fun convert(domainObject: T): Map<String, Any?>
}
