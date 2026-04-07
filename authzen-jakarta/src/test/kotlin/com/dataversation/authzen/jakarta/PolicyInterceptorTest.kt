/*
 * SPDX-FileCopyrightText: 2026 Dataversation
 * SPDX-License-Identifier: EUPL-1.2+
 */
package com.dataversation.authzen.jakarta

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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.enterprise.context.ApplicationScoped
import jakarta.interceptor.InvocationContext
import com.dataversation.authzen.model.Subject as AuthZenSubject
import com.dataversation.authzen.model.Resource as AuthZenResource

// ─── Test service with @Policy annotations ──────────────────────────────────

@ApplicationScoped
class ProtectedService {

    @Policy
    @Action(name = "lezen")
    @Resource(type = "zaakNotitie")
    fun staticCheck(): String = "ok"

    @Policy
    @Action(name = "wijzigen")
    fun resourceParamCheck(@Resource item: TestDomainObject): String = "ok:${item.name}"

    @Policy
    @Action(name = "downloaden")
    @Resource(type = "document")
    fun resourcePropertyCheck(
        @Resource(property = "status") status: String,
        @Resource(property = "locked") locked: Boolean
    ): String = "ok:$status:$locked"

    fun unprotected(): String = "no-policy"
}

data class TestDomainObject(val name: String, val type: String)

// ─── Test ResourceConverter ─────────────────────────────────────────────────

@ApplicationScoped
class TestDomainObjectConverter : ResourceConverter<TestDomainObject> {
    override val domainType: Class<TestDomainObject> = TestDomainObject::class.java
    override fun convert(domainObject: TestDomainObject) = AuthZenResource(
        type = domainObject.type,
        properties = mapOf("name" to domainObject.name)
    )
}

// ─── Test SubjectProvider ───────────────────────────────────────────────────

@ApplicationScoped
class TestSubjectProvider : SubjectProvider {
    override fun provide(context: InvocationContext, current: AuthZenSubject?): AuthZenSubject =
        AuthZenSubject(type = "user", id = "test-user")
}

// ─── Mock AccessService ─────────────────────────────────────────────────────

@ApplicationScoped
class MockAccessService : AccessService {

    var lastRequest: EvaluationRequest? = null
    var allowAll: Boolean = true

    override fun evaluation(request: EvaluationRequest): EvaluationResponse {
        lastRequest = request
        return EvaluationResponse(decision = allowAll)
    }

    override fun evaluations(request: EvaluationsRequest): EvaluationsResponse =
        throw UnsupportedOperationException()

    override fun searchActions(request: ActionSearchRequest): ActionSearchResponse =
        throw UnsupportedOperationException()

    override fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse =
        throw UnsupportedOperationException()

    override fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse =
        throw UnsupportedOperationException()
}

// ─── Weld SE test ───────────────────────────────────────────────────────────

class PolicyInterceptorTest : BehaviorSpec({

    Context("Static @Policy check (method-level @Action + @Resource)") {
        Given("a CDI container with the interceptor enabled") {
            val weld = org.jboss.weld.environment.se.Weld()
                .addBeanClass(ProtectedService::class.java)
                .addBeanClass(MockAccessService::class.java)
                .addBeanClass(TestSubjectProvider::class.java)
                .addBeanClass(TestDomainObjectConverter::class.java)
                .addBeanClass(PolicyInterceptor::class.java)
                .addBeanClass(ConverterRegistry::class.java)
                .addInterceptor(PolicyInterceptor::class.java)
            val container = weld.initialize()

            val service = container.select(ProtectedService::class.java).get()
            val accessService = container.select(MockAccessService::class.java).get()

            When("a method with @Action and @Resource is called and access is allowed") {
                accessService.allowAll = true
                val result = service.staticCheck()

                Then("the method executes and the evaluation request has correct action and resource") {
                    result shouldBe "ok"
                    accessService.lastRequest shouldNotBe null
                    accessService.lastRequest!!.action!!.name shouldBe "lezen"
                    accessService.lastRequest!!.resource!!.type shouldBe "zaakNotitie"
                    accessService.lastRequest!!.subject!!.type shouldBe "user"
                    accessService.lastRequest!!.subject!!.id shouldBe "test-user"
                }
            }

            When("access is denied") {
                accessService.allowAll = false

                Then("PolicyException is thrown") {
                    shouldThrow<PolicyException> {
                        service.staticCheck()
                    }
                }
            }

            afterSpec { container.shutdown() }
        }
    }

    Context("@Resource on a method parameter with converter") {
        Given("a CDI container with a ResourceConverter for TestDomainObject") {
            val weld = org.jboss.weld.environment.se.Weld()
                .addBeanClass(ProtectedService::class.java)
                .addBeanClass(MockAccessService::class.java)
                .addBeanClass(TestSubjectProvider::class.java)
                .addBeanClass(TestDomainObjectConverter::class.java)
                .addBeanClass(PolicyInterceptor::class.java)
                .addBeanClass(ConverterRegistry::class.java)
                .addInterceptor(PolicyInterceptor::class.java)
            val container = weld.initialize()

            val service = container.select(ProtectedService::class.java).get()
            val accessService = container.select(MockAccessService::class.java).get()

            When("a method with @Resource on a parameter is called") {
                accessService.allowAll = true
                val item = TestDomainObject(name = "test-item", type = "zaak")
                val result = service.resourceParamCheck(item)

                Then("the converter is used and the resource is correctly built") {
                    result shouldBe "ok:test-item"
                    accessService.lastRequest!!.action!!.name shouldBe "wijzigen"
                    accessService.lastRequest!!.resource!!.type shouldBe "zaak"
                    accessService.lastRequest!!.resource!!.properties!!["name"] shouldBe "test-item"
                }
            }

            afterSpec { container.shutdown() }
        }
    }

    Context("@Resource(property=...) on parameters") {
        Given("a CDI container") {
            val weld = org.jboss.weld.environment.se.Weld()
                .addBeanClass(ProtectedService::class.java)
                .addBeanClass(MockAccessService::class.java)
                .addBeanClass(TestSubjectProvider::class.java)
                .addBeanClass(TestDomainObjectConverter::class.java)
                .addBeanClass(PolicyInterceptor::class.java)
                .addBeanClass(ConverterRegistry::class.java)
                .addInterceptor(PolicyInterceptor::class.java)
            val container = weld.initialize()

            val service = container.select(ProtectedService::class.java).get()
            val accessService = container.select(MockAccessService::class.java).get()

            When("a method with @Resource(property=...) parameters is called") {
                accessService.allowAll = true
                val result = service.resourcePropertyCheck("definitief", true)

                Then("the properties are collected into the resource") {
                    result shouldBe "ok:definitief:true"
                    accessService.lastRequest!!.resource!!.type shouldBe "document"
                    accessService.lastRequest!!.resource!!.properties!!["status"] shouldBe "definitief"
                    accessService.lastRequest!!.resource!!.properties!!["locked"] shouldBe true
                }
            }

            afterSpec { container.shutdown() }
        }
    }

    Context("Unprotected methods") {
        Given("a CDI container") {
            val weld = org.jboss.weld.environment.se.Weld()
                .addBeanClass(ProtectedService::class.java)
                .addBeanClass(MockAccessService::class.java)
                .addBeanClass(TestSubjectProvider::class.java)
                .addBeanClass(TestDomainObjectConverter::class.java)
                .addBeanClass(PolicyInterceptor::class.java)
                .addBeanClass(ConverterRegistry::class.java)
                .addInterceptor(PolicyInterceptor::class.java)
            val container = weld.initialize()

            val service = container.select(ProtectedService::class.java).get()
            val accessService = container.select(MockAccessService::class.java).get()

            When("an unprotected method is called while access is denied") {
                accessService.allowAll = false
                accessService.lastRequest = null
                val result = service.unprotected()

                Then("no evaluation is performed and the method executes normally") {
                    result shouldBe "no-policy"
                    accessService.lastRequest shouldBe null
                }
            }

            afterSpec { container.shutdown() }
        }
    }
})
