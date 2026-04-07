/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import com.dataversation.authzen.model.Subject as AuthZenSubject

/**
 * Converts a domain object to an AuthZEN [Subject][AuthZenSubject].
 *
 * Register implementations as CDI beans; they are discovered automatically
 * by the [ConverterRegistry].
 */
interface SubjectConverter<T : Any> {
    val domainType: Class<T>
    fun convert(domainObject: T): AuthZenSubject
}
