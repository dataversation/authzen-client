/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.composite

import com.dataversation.authzen.AccessService
import com.dataversation.authzen.model.ActionSearchRequest
import com.dataversation.authzen.model.ActionSearchResponse
import com.dataversation.authzen.model.EvaluationRequest
import com.dataversation.authzen.model.EvaluationResponse
import com.dataversation.authzen.model.EvaluationsRequest
import com.dataversation.authzen.model.EvaluationsResponse
import com.dataversation.authzen.model.ResourceSearchRequest
import com.dataversation.authzen.model.ResourceSearchResponse
import com.dataversation.authzen.model.SubjectSearchRequest
import com.dataversation.authzen.model.SubjectSearchResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Combines two [AccessService] instances using a configurable [MergeStrategy].
 *
 * This enables layered authorization: for example, an application ships with a
 * built-in Kotlin policy (primary) and a customer deploys an external PDP (secondary)
 * that can selectively adjust permissions.
 *
 * The secondary PDP is only consulted when it could change the outcome:
 * - [MergeStrategy.AND]: secondary is skipped when primary denies (result is already `false`)
 * - [MergeStrategy.OR]: secondary is skipped when primary permits (result is already `true`)
 * - [MergeStrategy.OVERRIDE]: secondary is always consulted (it takes precedence)
 *
 * When [parallel] is `true`, both PDPs are called concurrently. The secondary call is
 * cancelled if the primary result allows short-circuiting. This reduces latency when
 * the secondary PDP is an external service, at the cost of potentially wasted work.
 *
 * @param primary The first AccessService to evaluate.
 * @param secondary The second AccessService to evaluate.
 * @param strategy How to combine the two results.
 * @param parallel Whether to call both PDPs concurrently (default: `false`).
 */
class CompositeAccessService @JvmOverloads constructor(
    private val primary: AccessService,
    private val secondary: AccessService,
    private val strategy: MergeStrategy = MergeStrategy.AND,
    private val parallel: Boolean = false
) : AccessService {

    private val executor: ExecutorService by lazy {
        Executors.newVirtualThreadPerTaskExecutor()
    }

    override fun evaluation(request: EvaluationRequest): EvaluationResponse {
        if (!parallel) {
            val primaryResult = primary.evaluation(request)
            if (strategy.canShortCircuit(primaryResult.decision)) return primaryResult
            val secondaryResult = secondary.evaluation(request)
            return EvaluationResponse(
                decision = strategy.merge(primaryResult.decision, secondaryResult.decision)
            )
        }

        val secondaryFuture = CompletableFuture.supplyAsync({ secondary.evaluation(request) }, executor)
        val primaryResult = primary.evaluation(request)
        if (strategy.canShortCircuit(primaryResult.decision)) {
            secondaryFuture.cancel(true)
            return primaryResult
        }
        val secondaryResult = secondaryFuture.join()
        return EvaluationResponse(
            decision = strategy.merge(primaryResult.decision, secondaryResult.decision)
        )
    }

    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
        if (!parallel) {
            val primaryResult = primary.evaluations(request)
            if (primaryResult.evaluations.all { strategy.canShortCircuit(it.decision) }) return primaryResult
            val secondaryResult = secondary.evaluations(request)
            return mergeEvaluations(primaryResult, secondaryResult)
        }

        val secondaryFuture = CompletableFuture.supplyAsync({ secondary.evaluations(request) }, executor)
        val primaryResult = primary.evaluations(request)
        if (primaryResult.evaluations.all { strategy.canShortCircuit(it.decision) }) {
            secondaryFuture.cancel(true)
            return primaryResult
        }
        val secondaryResult = secondaryFuture.join()
        return mergeEvaluations(primaryResult, secondaryResult)
    }

    private fun mergeEvaluations(
        primaryResult: EvaluationsResponse,
        secondaryResult: EvaluationsResponse
    ) = EvaluationsResponse(
        evaluations = primaryResult.evaluations.zip(secondaryResult.evaluations) { p, s ->
            EvaluationResponse(decision = strategy.merge(p.decision, s.decision))
        }
    )

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse =
        throw UnsupportedOperationException("Search not supported on composite")

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse =
        throw UnsupportedOperationException("Search not supported on composite")

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse =
        throw UnsupportedOperationException("Search not supported on composite")
}

/**
 * Strategy for combining decisions from two AccessService instances.
 *
 * Both decisions are nullable: `null` means the PDP abstains (has no opinion).
 * When a PDP abstains, only the other's decision counts. When both abstain,
 * the result is `false` (deny by default).
 *
 * **Note on abstention:** The AuthZEN 1.0 specification requires `decision` to be a
 * non-nullable boolean. Nullable decisions are a non-standard extension for PDPs
 * that can express "no opinion" through a custom convention.
 */
enum class MergeStrategy {

    /**
     * Both services must allow — logical AND.
     *
     * If one abstains, the other's decision is used.
     * If both abstain, access is denied.
     *
     * Short-circuits: skips secondary when primary denies.
     *
     * Use case: defense in depth — both the built-in policy and the external
     * PDP must agree before access is granted.
     */
    AND {
        override fun merge(primary: Boolean?, secondary: Boolean?) = when {
            primary == null && secondary == null -> false
            primary == null -> secondary!!
            secondary == null -> primary
            else -> primary && secondary
        }

        override fun canShortCircuit(primaryDecision: Boolean) = !primaryDecision
    },

    /**
     * Either service can allow — logical OR.
     *
     * If one abstains, the other's decision is used.
     * If both abstain, access is denied.
     *
     * Short-circuits: skips secondary when primary permits.
     *
     * Use case: the external PDP can grant additional permissions beyond
     * what the built-in policy allows.
     */
    OR {
        override fun merge(primary: Boolean?, secondary: Boolean?) = when {
            primary == null && secondary == null -> false
            primary == null -> secondary!!
            secondary == null -> primary
            else -> primary || secondary
        }

        override fun canShortCircuit(primaryDecision: Boolean) = primaryDecision
    },

    /**
     * Secondary overrides primary when secondary has an opinion (non-null).
     *
     * If the secondary returns a non-null decision, that decision wins regardless
     * of the primary. If the secondary abstains (null), the primary's decision
     * is used. If both abstain, access is denied.
     *
     * Never short-circuits: secondary is always consulted.
     *
     * Use case: application ships with complete defaults, customer PDP
     * can selectively override specific actions for specific users.
     */
    OVERRIDE {
        override fun merge(primary: Boolean?, secondary: Boolean?) =
            secondary ?: primary ?: false

        override fun canShortCircuit(primaryDecision: Boolean) = false
    };

    abstract fun merge(primary: Boolean?, secondary: Boolean?): Boolean

    /**
     * Whether the primary decision alone determines the outcome,
     * making the secondary call unnecessary.
     */
    abstract fun canShortCircuit(primaryDecision: Boolean): Boolean
}
