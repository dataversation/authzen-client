/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.composite

import com.dataversation.authzen.AccessService
import com.dataversation.authzen.model.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CompositeAccessServiceTest : DescribeSpec({

    // ─── Helpers ────────────────────────────────────────────────────────

    fun request(action: String = "read") = EvaluationRequest(
        subject = Subject(type = "user", id = "alice"),
        action = Action(name = action),
        resource = Resource(type = "document", id = "doc-1")
    )

    fun batchRequest(vararg actions: String) = EvaluationsRequest(
        subject = Subject(type = "user", id = "alice"),
        resource = Resource(type = "document", id = "doc-1"),
        evaluations = actions.map { EvaluationRequest(action = Action(name = it)) }
    )

    /** A stub PDP that returns fixed decisions and tracks whether it was called. */
    class StubPdp(
        private val singleDecision: Boolean = false,
        private val batchDecisions: List<Boolean> = emptyList(),
        private val delay: Long = 0
    ) : AccessService {
        val called = AtomicBoolean(false)
        val callCount = AtomicInteger(0)

        override fun evaluation(request: EvaluationRequest): EvaluationResponse {
            called.set(true)
            callCount.incrementAndGet()
            if (delay > 0) Thread.sleep(delay)
            return EvaluationResponse(decision = singleDecision)
        }

        override fun evaluations(request: EvaluationsRequest): EvaluationsResponse {
            called.set(true)
            callCount.incrementAndGet()
            if (delay > 0) Thread.sleep(delay)
            val decisions = if (batchDecisions.isNotEmpty()) batchDecisions
                else request.evaluations.map { singleDecision }
            return EvaluationsResponse(evaluations = decisions.map { EvaluationResponse(decision = it) })
        }

        override fun searchActions(request: ActionSearchRequest) = throw UnsupportedOperationException()
        override fun searchSubjects(request: SubjectSearchRequest) = throw UnsupportedOperationException()
        override fun searchResources(request: ResourceSearchRequest) = throw UnsupportedOperationException()
    }

    // ─── AND strategy ───────────────────────────────────────────────────

    describe("AND strategy") {
        it("permits when both PDPs permit") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = true)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND)
            composite.evaluation(request()).decision shouldBe true
            secondary.called.get() shouldBe true
        }

        it("denies when primary denies — secondary is NOT called") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = true)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND)
            composite.evaluation(request()).decision shouldBe false
            secondary.called.get() shouldBe false
        }

        it("denies when primary permits but secondary denies") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND)
            composite.evaluation(request()).decision shouldBe false
        }

        it("denies when both deny") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND)
            composite.evaluation(request()).decision shouldBe false
            secondary.called.get() shouldBe false // short-circuited
        }
    }

    // ─── OR strategy ────────────────────────────────────────────────────

    describe("OR strategy") {
        it("permits when primary permits — secondary is NOT called") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR)
            composite.evaluation(request()).decision shouldBe true
            secondary.called.get() shouldBe false
        }

        it("permits when primary denies but secondary permits") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = true)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR)
            composite.evaluation(request()).decision shouldBe true
        }

        it("denies when both deny") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR)
            composite.evaluation(request()).decision shouldBe false
            secondary.called.get() shouldBe true // no short-circuit possible
        }

        it("permits when both permit") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = true)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR)
            composite.evaluation(request()).decision shouldBe true
            secondary.called.get() shouldBe false // short-circuited
        }
    }

    // ─── OVERRIDE strategy ──────────────────────────────────────────────

    describe("OVERRIDE strategy") {
        it("secondary decision wins regardless of primary") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OVERRIDE)
            composite.evaluation(request()).decision shouldBe false
        }

        it("secondary permit overrides primary deny") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = true)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OVERRIDE)
            composite.evaluation(request()).decision shouldBe true
        }

        it("always calls secondary — never short-circuits") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OVERRIDE)
            composite.evaluation(request()).decision shouldBe false
            secondary.called.get() shouldBe true
        }
    }

    // ─── Batch evaluations ──────────────────────────────────────────────

    describe("batch evaluations") {
        it("AND: skips secondary when ALL primary decisions deny") {
            val primary = StubPdp(batchDecisions = listOf(false, false, false))
            val secondary = StubPdp(batchDecisions = listOf(true, true, true))
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND)
            val result = composite.evaluations(batchRequest("read", "write", "delete"))
            result.evaluations.map { it.decision } shouldBe listOf(false, false, false)
            secondary.called.get() shouldBe false
        }

        it("AND: calls secondary when ANY primary decision permits") {
            val primary = StubPdp(batchDecisions = listOf(true, false, false))
            val secondary = StubPdp(batchDecisions = listOf(true, true, false))
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND)
            val result = composite.evaluations(batchRequest("read", "write", "delete"))
            // read: true && true = true, write: false && true = false, delete: false && false = false
            result.evaluations.map { it.decision } shouldBe listOf(true, false, false)
            secondary.called.get() shouldBe true
        }

        it("OR: skips secondary when ALL primary decisions permit") {
            val primary = StubPdp(batchDecisions = listOf(true, true))
            val secondary = StubPdp(batchDecisions = listOf(false, false))
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR)
            val result = composite.evaluations(batchRequest("read", "write"))
            result.evaluations.map { it.decision } shouldBe listOf(true, true)
            secondary.called.get() shouldBe false
        }

        it("OR: calls secondary when ANY primary decision denies") {
            val primary = StubPdp(batchDecisions = listOf(true, false))
            val secondary = StubPdp(batchDecisions = listOf(false, true))
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR)
            val result = composite.evaluations(batchRequest("read", "write"))
            // read: true || false = true, write: false || true = true
            result.evaluations.map { it.decision } shouldBe listOf(true, true)
            secondary.called.get() shouldBe true
        }
    }

    // ─── Parallel mode ──────────────────────────────────────────────────

    describe("parallel mode") {
        it("AND: cancels secondary when primary denies") {
            val latch = CountDownLatch(1)
            val secondaryCalled = AtomicBoolean(false)
            val slowSecondary = object : AccessService {
                override fun evaluation(request: EvaluationRequest): EvaluationResponse {
                    latch.await() // block until cancelled or released
                    secondaryCalled.set(true)
                    return EvaluationResponse(decision = true)
                }
                override fun evaluations(request: EvaluationsRequest) = throw UnsupportedOperationException()
                override fun searchActions(request: ActionSearchRequest) = throw UnsupportedOperationException()
                override fun searchSubjects(request: SubjectSearchRequest) = throw UnsupportedOperationException()
                override fun searchResources(request: ResourceSearchRequest) = throw UnsupportedOperationException()
            }
            val primary = StubPdp(singleDecision = false)
            val composite = CompositeAccessService(primary, slowSecondary, MergeStrategy.AND, parallel = true)
            val result = composite.evaluation(request())
            result.decision shouldBe false
            latch.countDown() // release the blocked thread
            Thread.sleep(50) // give it a moment
            secondaryCalled.get() shouldBe false
        }

        it("OR: cancels secondary when primary permits") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = false, delay = 500)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OR, parallel = true)
            val start = System.currentTimeMillis()
            val result = composite.evaluation(request())
            val elapsed = System.currentTimeMillis() - start
            result.decision shouldBe true
            // should return much faster than secondary's 500ms delay
            (elapsed < 200) shouldBe true
        }

        it("AND: waits for secondary when primary permits") {
            val primary = StubPdp(singleDecision = true)
            val secondary = StubPdp(singleDecision = false, delay = 50)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND, parallel = true)
            val result = composite.evaluation(request())
            result.decision shouldBe false // secondary denies
            secondary.called.get() shouldBe true
        }

        it("OVERRIDE: always waits for secondary") {
            val primary = StubPdp(singleDecision = false)
            val secondary = StubPdp(singleDecision = true, delay = 50)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.OVERRIDE, parallel = true)
            val result = composite.evaluation(request())
            result.decision shouldBe true // secondary overrides
            secondary.called.get() shouldBe true
        }

        it("runs both PDPs concurrently — faster than sequential") {
            val primary = StubPdp(singleDecision = true, delay = 100)
            val secondary = StubPdp(singleDecision = true, delay = 100)
            val composite = CompositeAccessService(primary, secondary, MergeStrategy.AND, parallel = true)
            val start = System.currentTimeMillis()
            composite.evaluation(request()).decision shouldBe true
            val elapsed = System.currentTimeMillis() - start
            // parallel should complete in ~100ms, sequential would take ~200ms
            (elapsed < 180) shouldBe true
        }
    }
})
