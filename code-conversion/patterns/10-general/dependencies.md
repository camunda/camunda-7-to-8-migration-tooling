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

**Spring Boot version**: Select the starter from the [Camunda Spring Boot version compatibility matrix](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/getting-started/#version-compatibility). For Camunda 8.9, `camunda-spring-boot-3-starter` is for Spring Boot 3.5.x. `camunda-spring-boot-starter` is bundled with Spring Boot 4.0.x and supports Spring Boot 4.1.x from 8.9.12:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-spring-boot-3-starter</artifactId>
	<version>{version}</version>
</dependency>
```

**Startup validation**: When a project uses a Camunda Spring Boot starter, boot an application context
that creates `CamundaClient`. If startup fails, record a blocking finding. Do not override individual
transitive dependencies to force startup.

**Version resolution**: Resolve the latest released GA version from Maven Central's direct artifact metadata, for example `https://repo.maven.apache.org/maven2/io/camunda/<artifact-id>/maven-metadata.xml` (the equivalent `repo1.maven.org` path is also available). From `<versions>`, select the highest version matching the target Camunda minor (`8.8.x`, `8.9.x`, etc.) and exclude `-SNAPSHOT`, `-alpha`, `-beta`, and `-rc` versions. If no GA version exists for the target, ask before using a pre-release. Do not use `search.maven.org`'s search API or the Camunda public repository metadata for this lookup.

**Java client artifact**: Use `io.camunda:camunda-client-java`. The legacy `io.camunda:zeebe-client-java` artifact is deprecated and will be discontinued in Camunda 8.10.

**Process test artifact**: Camunda 8.10 removes Zeebe Process Test. Replace `zeebe-process-test-extension` and `zeebe-process-test-extension-testcontainer` with `io.camunda:camunda-process-test-java`:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-process-test-java</artifactId>
	<version>{version}</version>
	<scope>test</scope>
</dependency>
```

For Spring Boot applications, use `camunda-process-test-spring` with the Spring Boot 4 starter or `camunda-process-test-spring-boot-3` with `camunda-spring-boot-3-starter`. The former `spring-boot-starter-camunda-test` and `spring-boot-starter-camunda-test-testcontainer` artifacts are replaced by these CPT Spring modules.

If the project uses the temporary `camunda-process-test-spring-4` or `camunda-process-test-spring-boot-4` artifact names from Camunda 8.8, replace them with `camunda-process-test-spring`.

**Logging backend**: When removing Camunda 7 webapp/rest starters, keep an SLF4J binding. If those starters were your only logging source, add `org.springframework.boot:spring-boot-starter-logging` (or another SLF4J backend) so startup failures remain visible.

**`jakarta.annotation` and process startup**: If `@PostConstruct` remains only to start process instances, migrate that startup to `@EventListener(CamundaPostDeploymentEvent.class)` first. Prefer fixing that lifecycle pattern over adding dependencies (for example `jakarta.annotation-api`) solely to keep `@PostConstruct`.