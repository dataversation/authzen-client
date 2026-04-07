/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.enterprise.util.Nonbinding

/**
 * Specifies the subject for an AuthZEN evaluation request.
 *
 * **Method-level:** provides a static subject type, e.g. `@Subject(type = "user")`.
 *
 * **Parameter-level (no [property]):** the parameter IS the subject — resolved via a
 * [SubjectConverter] or used directly if it is an [com.dataversation.authzen.model.Subject].
 *
 * **Parameter-level (with [property]):** the parameter value is serialized into
 * `subject.properties[property]`.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subject(
    @get:Nonbinding val type: String = "",
    @get:Nonbinding val property: String = ""
)
