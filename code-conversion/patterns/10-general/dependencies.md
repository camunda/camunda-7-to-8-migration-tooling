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

**Startup validation**: When a project uses a Camunda Spring Boot starter, boot an application context that creates `CamundaClient`. If startup fails, record a blocking finding. Do not override individual transitive dependencies to force startup.