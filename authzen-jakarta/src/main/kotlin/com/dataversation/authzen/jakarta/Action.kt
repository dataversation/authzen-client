/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

import jakarta.enterprise.util.Nonbinding

/**
 * Specifies the action for an AuthZEN evaluation request.
 *
 * **Method-level:** provides a static action name, e.g. `@Action(name = "lezen")`.
 *
 * **Parameter-level (no [property]):** the parameter IS the action — resolved via an
 * [ActionConverter] or used directly if it is an [com.dataversation.authzen.model.Action].
 *
 * **Parameter-level (with [property]):** the parameter value is serialized into
 * `action.properties[property]`.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Action(
    @get:Nonbinding val name: String = "",
    @get:Nonbinding val property: String = ""
)
