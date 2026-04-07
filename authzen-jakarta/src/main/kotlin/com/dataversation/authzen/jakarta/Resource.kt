/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.enterprise.util.Nonbinding

/**
 * Specifies the resource for an AuthZEN evaluation request.
 *
 * **Method-level:** provides a static resource type, e.g. `@Resource(type = "zaak")`.
 *
 * **Parameter-level (no [property]):** the parameter IS the resource — resolved via a
 * [ResourceConverter] or used directly if it is an [com.dataversation.authzen.model.Resource].
 *
 * **Parameter-level (with [property]):** the parameter value is serialized into
 * `resource.properties[property]`.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Resource(
    @get:Nonbinding val type: String = "",
    @get:Nonbinding val property: String = ""
)
