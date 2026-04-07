# authzen-client

Kotlin/JVM client library for the [OpenID AuthZEN Authorization API 1.0](https://openid.net/specs/authorization-api-1_0.html), with pluggable PDP adapters for multiple authorization engines.

> **Alpha software** -- APIs, module structure, and adapter behaviour may change without notice. Not recommended for production use without thorough testing.

## Modules

| Module | Description |
|--------|-------------|
| `authzen-api` | Core `AccessService` interface and AuthZEN data model (`Subject`, `Resource`, `Action`, `EvaluationRequest`, etc.) |
| `authzen-http` | Generic HTTP adapter -- works with any PDP that natively speaks the AuthZEN Evaluations API |
| `authzen-grpc` | Generic gRPC adapter -- works with any PDP that implements the AuthZEN gRPC service |
| `authzen-topaz` | [Topaz](https://www.topaz.sh/) adapter via the native `is` API with multi-decision batching |
| `authzen-cerbos` | [Cerbos](https://cerbos.dev/) adapter via the CheckResources API |
| `authzen-spicedb` | [SpiceDB](https://authzed.com/spicedb) adapter via the Permissions API with bulk checks |
| `authzen-authzforce` | [AuthzForce CE](https://github.com/authzforce/server) adapter via XACML 3.0 JSON Profile with Multiple Decision Profile (MDP) |

## AccessService interface

All adapters implement `AccessService`:

```kotlin
interface AccessService {
    fun evaluation(request: EvaluationRequest): EvaluationResponse
    fun evaluations(request: EvaluationsRequest): EvaluationsResponse
    fun searchActions(request: ActionSearchRequest): ActionSearchResponse
    fun searchSubjects(request: SubjectSearchRequest): SubjectSearchResponse
    fun searchResources(request: ResourceSearchRequest): ResourceSearchResponse
}
```

The current focus of this project is the `evaluations()` endpoint -- each adapter translates an `EvaluationsRequest` into the most efficient native API call for the target PDP. Support for the other endpoints may be added in the future.

## Adapter scope and limitations

### What the adapters do

- Translate AuthZEN `EvaluationsRequest` into native PDP requests
- Map `subject.properties.*` and `resource.properties.*` generically (no application-specific field knowledge)
- Batch multiple action evaluations into a single PDP call where possible
- Return per-action boolean decisions

### What the adapters do not do

- **No Search API support** -- `searchActions`, `searchSubjects`, and `searchResources` throw `UnsupportedOperationException` on all adapters. Use `evaluations()` instead.
- **Limited per-evaluation overrides** -- Each evaluation can override the `action`. Per-evaluation `subject`, `resource`, and `context` overrides are not supported.
- **No policy management** -- Adapters only evaluate decisions; they do not deploy, update, or manage policies.

### Per-adapter notes

| Adapter | Protocol | Batching | Notes |
|---------|----------|----------|-------|
| HTTP | AuthZEN Evaluations API | Native | Requires PDP with AuthZEN support |
| gRPC | AuthZEN gRPC | Native | Requires PDP with AuthZEN gRPC support |
| Topaz | Topaz `is` API | Multi-decision in one call | Requires `regoPackagePrefix` and optional `resourceTypeMap` for policy path resolution |
| Cerbos | CheckResources API | All actions in one call | Subject properties are passed as Cerbos principal attributes |
| SpiceDB | Permissions BulkCheck API | All permissions in one call | Supports auto-provisioning of role-resource relationships via `roleAssignments` and an optional `preCheck` predicate |
| AuthzForce | XACML JSON + MDP | All actions in one call | Generically maps all AuthZEN attributes to XACML categories; collections become multi-valued bags |

## Usage

Add the core API and desired adapter(s) as dependencies:

```kotlin
implementation("com.dataversation.authzen:authzen-api:0.2.0-SNAPSHOT")
runtimeOnly("com.dataversation.authzen:authzen-topaz:0.2.0-SNAPSHOT")
```

Instantiate the adapter:

```kotlin
val accessService = TopazAccessService(
    baseUrl = "http://topaz:8383",
    regoPackagePrefix = "myapp",
    resourceTypeMap = mapOf("mySpecialType" to "special")
)
```

Evaluate permissions:

```kotlin
val response = accessService.evaluations(EvaluationsRequest(
    subject = Subject(type = "user", id = "alice", properties = mapOf("roles" to listOf("editor"))),
    resource = Resource(type = "document", id = "doc-123"),
    evaluations = listOf(
        EvaluationRequest(action = Action(name = "view")),
        EvaluationRequest(action = Action(name = "edit")),
        EvaluationRequest(action = Action(name = "delete"))
    )
))

response.evaluations.forEach { println(it.decision) } // true, true, false
```

## License

[EUPL-1.2+](LICENSE)
