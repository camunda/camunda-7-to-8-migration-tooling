# Spring Boot Build Wiring

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Use this procedure when the migration creates or retains a Spring Boot application entry point.
The entry point is a class annotated with `@SpringBootApplication`, or an equivalent explicitly
designated Spring Boot main class.

## Build inventory

Before changing code or build files, record these facts in `MIGRATION_REPORT.md`:

| Fact | Required value |
|---|---|
| Build tool | Maven, Gradle, or another supported tool |
| Application entry point | Fully qualified class name and source path |
| Packaging | `jar`, `war`, or another declared type |
| Existing application plugin | Plugin id, version source, executions, and configuration |
| Plugin management | Parent or `pluginManagement` declarations that can provide a version |
| Launch path | The command that starts the application |
| Intended runtime | Runnable application, test-only support, or externally managed |

Search every module build file, parent build file, and effective build model. Do not infer plugin
configuration from a dependency alone.

## Maven application modules

When a Maven module has a runtime Spring Boot entry point, apply the following decision table in order:

| Effective Maven state | Required action |
|---|---|
| `spring-boot-maven-plugin` is declared under `build/plugins` with a direct or effective managed version | Keep its version, executions, and configuration. Add only missing run or packaging configuration. |
| `spring-boot-maven-plugin` is declared under `build/plugins` without a direct or effective managed version | Add the selected Spring Boot version. Record its source and compatibility check. Keep existing executions and configuration. |
| The plugin exists only under `build/pluginManagement` or a parent manages only its version | Add the plugin under the module's `build/plugins` and inherit the managed version. |
| No plugin declaration or managed version exists | Add the plugin with the Spring Boot version selected for the migrated module. Record the source and compatibility check. |
| The module has no runtime Spring Boot entry point | Do not add the plugin. Record the supported non-application execution path. |

Do not infer a Maven plugin version from a dependency BOM. Dependency management supplies dependency
versions, not build-plugin versions.

Preserve existing plugin executions and configuration. Merge only the minimum required properties.
Declare the plugin so `mvn spring-boot:run` resolves without a fully qualified temporary plugin
invocation.

Ensure the effective build invokes `repackage` during `package`, either through an existing
execution or through a minimal execution added to the plugin. Do not add a second `repackage`
execution when a parent already supplies one. If more than one main class is discoverable, set the
generated application's fully qualified class name as the plugin `mainClass`.

Keep the module's existing packaging type unless the migration explicitly changes it. Do not add
`spring-boot-starter-web` or another runtime dependency only to make the plugin run. The application
plugin supplies build and launch integration. Dependencies remain governed by the dependency
transformation checklist.

## Test-only and externally managed modules

If the migrated code is test-only, do not create a misleading `@SpringBootApplication` entry point
or add an application plugin. Record the supported test command in `MIGRATION_REPORT.md`.

If an external launcher owns the runtime, record its command and ownership. Keep the module plugin
configuration unchanged unless the external launcher requires the executable artifact.

## Validation

Run the validation for the intended runtime recorded in the inventory:

| Intended runtime | Required validation |
|---|---|
| Runtime Spring Boot application | Run `mvn spring-boot:run` from the module directory. Confirm that Maven resolves the plugin and that the selected entry point starts. Run `mvn package` and inspect the produced executable artifact, such as a JAR or executable WAR. Confirm that it contains the Spring Boot loader and application classes. Start the artifact with `java -jar <artifact>`. Confirm the same entry point starts. |
| Test-only module | Run the module test command. Confirm that the module has no runtime entry point and did not acquire an application plugin. Do not run Spring Boot launch or executable-artifact checks. |
| Externally managed application | Run the recorded external launch command. If the launcher requires an executable artifact, run the package and artifact checks for the runtime application. Otherwise, record the Maven launch and artifact checks as not applicable. |

A missing Camunda cluster is a runtime environment failure. It does not show that plugin resolution
failed. Record the external dependency that prevents startup when a launch check cannot start the
application.

Record each command, exit code, artifact path, and any environment prerequisite in
`MIGRATION_REPORT.md`. When a command cannot run, keep the build-wiring finding open and record the
exact blocker. Do not report a plugin as validated from a successful compile alone.

## Regression fixture

Use `agentic-migration-skills/fixtures/spring-boot-maven-wiring` for a non-Boot Camunda 7 Maven
module that becomes a Camunda 8 Spring Boot deployment application. The fixture contains the source
build without an application plugin, an expected runtime copy, and an expected negative copy.
Repeat the validation commands against temporary copies and keep the source build unchanged.
