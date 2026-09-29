# HTTP Application and Engine REST Topology

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked
(SHOULD) and an option is marked (MAY).

An HTTP topology is the set of application routes, Engine REST calls, health checks, bind addresses,
and their consumers.

## Inventory

Complete this inventory before changing code. Record it in `MIGRATION_REPORT.md`.

| Surface | Record |
|---|---|
| Application server | Module, bind address (`server.address`), `server.port`, active profile, and environment overrides. |
| Management server | Bind address (`management.server.address`), `management.server.port`, exposed endpoints, and active health checks. |
| Application routes | HTTP method, path, controller or handler, authentication, consumers, and tests. |
| Camunda 7 Engine routes | Every `/engine-rest` or webapp route and the owning engine module. |
| Engine REST clients | HTTP method, path, request, response, authentication, caller, and business purpose. |
| Health checks | Each checked dependency and its health-check client's connection and response timeouts. Include the engine, database, and external services. |

Search Java and Kotlin sources, application configuration, build files, test sources, scripts, and
deployment configuration. Search for route annotations, servlet registrations, `RestTemplate`,
`WebClient`, `RestClient`, HTTP clients, URL strings, and `/engine-rest`.

## Classify each route and call

The embedded Camunda 7 engine could expose its REST API on the application's HTTP server. The
Camunda 8 Orchestration Cluster REST API is served by the external cluster. It does not preserve
the Camunda 7 `/engine-rest` contract.

| Source surface | Required decision |
|---|---|
| Application-owned route | Keep or change the deliberate API. If the migration removes it, record its consumers and a migration plan. |
| Camunda 7 Engine REST route | Do not recreate or proxy `/engine-rest`. Record the removed route and its consumers. |
| Call to a Camunda 7 Engine REST route | Use a documented CamundaClient or Orchestration Cluster API operation when one matches. Record a manual follow-up when no replacement is confirmed. |
| Health check | Preserve the source health scope. Check the real C8 client or cluster and each other source dependency. |

Do not infer a C8 API mapping from a similar URL. Check the selected target version's official API
documentation before replacing each Engine REST call. Record request and response differences and
update every affected consumer and test.

When the user selects a manual follow-up, name the call site, state the unresolved question, and set
its `MIGRATION_REPORT.md` status to `open`. Do not replace an endpoint with
`UnsupportedOperationException`, a constant success response, or an empty-context test.

## Decide application and cluster addresses

Ask Question 7 in `references/interview-questions.md` after the inventory. Record the user's explicit
target application bind address and port, the Camunda REST base address, and the authentication mode.
Record the user's endpoint decisions. Where the management server uses a separate bind address or
port, record both.
Do not guess a bind address, port, or cluster address.

When the application and cluster use the same host, configure different ports. For example, an
application can use `server.port=8081` while a local cluster uses REST port `8080`. Record the
management port separately when it differs.

For the Camunda Spring Boot Starter, `camunda.client.rest-address` configures the REST base address.
For a local self-managed cluster, the address can be `http://localhost:8080`. The Orchestration
Cluster REST API route is then under `/v2`, for example
`http://localhost:8080/v2/topology`. Use the selected target's official
[starter connectivity documentation](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#connectivity)
and [REST API documentation](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/)
to confirm the address and API.

Do not store credentials in `MIGRATION_REPORT.md`. Record the authentication method and redact
credential values.

## Validate the target topology

Run the application and the C8 cluster at the configured addresses at the same time. Confirm their
ports do not collide when they share a host.

Test each changed application endpoint through the application server. Test each replacement
Camunda operation through the selected C8 API. Assert the intended response or process behavior.
Do not accept a test that only loads the Spring context.

Configure finite connection and response timeouts for every remote HTTP health-check client.
When the source includes a health check, test each dependency while it responds and while it is
unavailable or timed out. Require the health check to report healthy and unhealthy, respectively.
Never preserve a constant `engineRest=ok` value.

Confirm that the application does not serve or proxy the old `/engine-rest` routes. Record the
commands, results, and any blocked checks in `MIGRATION_REPORT.md`. Leave a blocked check open when
the cluster or a required user decision is unavailable.

The [`spring-boot-web-topology` fixture](../../../fixtures/spring-boot-web-topology/README.md)
checks separate application and cluster ports, live health, process start through the application
API, and the absence of the old Engine REST route.
