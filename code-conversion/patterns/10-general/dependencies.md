# Maven dependency and configuration

As part of the code migration, classify every dependency before removal. Remove a Camunda 7 engine
dependency only when the project has no active use for it. Keep compatible domain libraries even
when their group ID starts with `org.camunda.bpm`. Import the **Camunda Spring SDK**:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-spring-boot-starter</artifactId>
	<version>{version}</version>
</dependency>
```

Also, configure your connection to the Camunda 8 cluster in the `application.properties` or `application.yaml`.

**Dependency inventory and classification**:

Inventory every dependency before classification. Check its imports, configuration, reflection,
service-provider registrations, and runtime call sites. Record each dependency, its uses, target
compatibility, and decision in `MIGRATION_REPORT.md`. Treat a group ID or package under
`org.camunda.bpm` as a review signal, not proof that the dependency is engine-only.

| Finding | Action |
|---|---|
| Camunda 7 engine or embedded-engine dependency with no Camunda 8 equivalent and no remaining required use | Remove it and record the reason. This can include `camunda-bom` or database dependencies used only by the embedded engine. |
| Active domain library that is compatible with the target runtime | Keep it, even when its group ID or package starts with `org.camunda.bpm`. |
| Active library with unknown target compatibility | Check its documented target support and test its required behavior. Ask the project owner to confirm compatibility or approve a replacement before changing behavior. If compatibility remains unconfirmed, then leave the active code unchanged. Record each affected call site as `blocked` with manual follow-up in `MIGRATION_REPORT.md`. Do not report those flows as migrated. |
| Active library is incompatible, and the project owner approves a replacement | Replace the library and its call sites only after recording the approval. Test the required behavior for every supported type and downstream call path. |
| Active library is incompatible and has no owner-approved replacement | Leave the active code unchanged. Record each affected call site as `blocked` with manual follow-up in `MIGRATION_REPORT.md`. Do not replace behavior with an exception or fabricated result. Do not report those flows as migrated. |

For active license-generation behavior, test every supported license type and downstream call path
with synthetic fixture values. A successful compile alone does not prove that the behavior still
works. Never invent license keys or other business data. Never include production keys or
credentials in fixtures.

**Spring Boot version**: Select the starter from the [Camunda Spring Boot version compatibility matrix](https://docs.camunda.io/docs/8.8/apis-tools/camunda-spring-boot-starter/getting-started/#version-compatibility). For Camunda 8.8, `camunda-spring-boot-starter` is bundled with Spring Boot 3.5.x. Use `camunda-spring-boot-4-starter` from 8.8.9 for Spring Boot 4.0.x. Use `camunda-spring-boot-3-starter` from 8.8.15 when staying on Spring Boot 3.x.

**BOM alignment**: Preserve existing parent dependency management and imported BOMs unless
inspection supports an explicit change. Inspect `mvn help:effective-pom -Dverbose` and
`mvn dependency:tree -Dverbose` for the Camunda starter and existing cloud libraries before changing
dependency management. Trace each imported BOM's effective version to the POM or property that
supplies it. If a BOM does not resolve, record its coordinates and the exact Maven error. Check its
coordinates, inherited version source, and configured repositories before replacing or removing it.
Never comment out an unresolved BOM and replace selected managed artifacts with isolated version
pins.

Check explicit `<version>` values on direct dependencies managed by an imported BOM. A direct
version overrides BOM management. Remove an unexplained override. Record the compatibility reason
for any retained override.

Run the same inspections after POM changes. For gRPC, inspect every resolved `io.grpc` artifact, its
dependency path, and its version source. Manage an incompatible gRPC family through a compatible
`io.grpc:grpc-bom` in `<dependencyManagement>`. Never pin only `grpc-xds`, `grpc-util`, or
`grpc-core`. Remove a conflicting direct dependency only after source, configuration, and test
searches show that the project does not use it. Record before-and-after versions, dependency paths,
version sources, and the remediation in `MIGRATION_REPORT.md`.

**Startup validation**: When a project uses a Camunda Spring Boot starter, boot an application context
that creates the real `CamundaClient` bean. Do not mock the bean or issue an API command in this
focused test. The test does not require a reachable cluster. If the focused test fails or cannot run,
block readiness and record its command, exit code, and error. Classify the failure as a classpath
incompatibility only when it reports a `LinkageError` or the resolved dependency graph proves an
incompatible family. A cluster connection failure during an API command is separate evidence and
never proves that the classpath is compatible.

**Camunda 7 test artifacts and replacements**: CPT Java and Spring test APIs are available from Camunda 8.8. Conditional behavior requires 8.9. Inventory the test dependencies before removing them. Keep dependencies that still support in-scope tests.

| Camunda 7 test artifact or use | Camunda 8 replacement | Note |
|---|---|---|
| `camunda-bpm-assert`, `camunda-bpm-junit5` | `io.camunda:camunda-process-test-java` | Use the matching CPT Spring artifact instead when the tests use the Camunda Spring Boot Starter. |
| `camunda-bpm-spring-boot-starter-test` | `io.camunda:camunda-process-test-spring` or `io.camunda:camunda-process-test-spring-boot-3` | Match the CPT artifact to the selected Camunda Spring Boot Starter. Both include the CPT Java API. For Camunda 8.8 with Spring Boot 4, use `camunda-process-test-spring-4` on 8.8.9–8.8.21 or `camunda-process-test-spring-boot-4` from 8.8.22. |
| `camunda-platform-7-mockito` | CPT job-worker, child-process, and DMN mock utilities | Keep Mockito for collaborator mocks. |
| `camunda-process-test-coverage` platform 7 modules | No extra dependency | CPT writes its own coverage report from 8.8. |
| `camunda-bpm-assert-scenario`, `camunda-platform-scenario-runner` | `io.camunda:camunda-process-test-java` | Conditional behavior requires 8.9. |
| Test-scoped `camunda-engine` used only by process tests | `io.camunda:camunda-process-test-java` | Remove the embedded engine only after confirming no other required use remains. |
| H2 used only by the embedded test engine | No replacement | Remove H2 only when the application does not use it. CPT runs against the Camunda runtime. |
| JUnit 4 tests that remain in the module but are not process tests | `org.junit.vintage:junit-vintage-engine` | Add it in test scope so the remaining JUnit 4 tests still run. |
