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

When Maven owns the runtime launch or an external launcher requires an executable artifact, apply the following decision table in order.

| Effective Maven state | Required action |
|---|---|
| `spring-boot-maven-plugin` is declared under `build/plugins` with a direct or effective managed version | Keep a compatible version, executions, and configuration. If the version is stale for the selected Spring Boot major, update only the version. Add only missing run or packaging configuration. |
| `spring-boot-maven-plugin` is declared under `build/plugins` without a direct or effective managed version | Add the selected Spring Boot version. Record its source and compatibility check. Keep existing executions and configuration. |
| The plugin is absent from `build/plugins`, and a direct or effective managed version exists in `build/pluginManagement` or a parent | Add the plugin under the module's `build/plugins` and inherit the managed version. |
| No plugin declaration or direct/effective managed version exists | Add the plugin with the Spring Boot version selected for the migrated module. Record the source and compatibility check. |
| The module has no runtime Spring Boot entry point | Do not add the plugin. Record the supported non-application execution path. |

Do not infer a Maven plugin version from a dependency BOM. Dependency management supplies dependency
versions, not build-plugin versions.

Preserve an existing Maven `<parent>`. Do not replace it with `spring-boot-starter-parent` to obtain
Spring Boot management. If the existing parent does not provide compatible Spring Boot dependency
management, import a compatible `spring-boot-dependencies` BOM and manage the Maven plugin through
`pluginManagement` or an explicit compatible version.

Preserve existing plugin executions and configuration. Merge only the minimum required properties.
Declare the plugin so `mvn spring-boot:run` resolves without a fully qualified temporary plugin
invocation.

Compare every existing plugin version with the selected Spring Boot major. Record the compatibility
check and its result. Update only a stale version to a compatible version.

Ensure the effective build invokes `repackage` during `package`, either through an existing
execution or through a minimal execution added to the plugin. Do not add a second `repackage`
execution when a parent already supplies one. Before preserving plugin configuration, compare any
existing `mainClass` with the selected entry point. If the values differ, replace `mainClass` with
the selected entry point. If multiple main classes are discoverable, then the skill sets
`mainClass` to the selected application's fully qualified class name.

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

The skill records each command, exit code, artifact path, and non-secret environment prerequisites
in `MIGRATION_REPORT.md`. Before the skill records a command, it replaces secret values in the
command and output with `<redacted>`. When the skill cannot run a command, it keeps the build-wiring
finding open and records only non-secret blocker details. The skill does not report a plugin as
validated from a successful compile alone.

## Regression fixture

Use `agentic-migration-skills/fixtures/spring-boot-maven-wiring` for a non-Boot Camunda 7 Maven
module that becomes a Camunda 8 Spring Boot deployment application. The fixture contains the source
build without an application plugin, an expected runtime copy, and an expected negative copy.
Repeat the validation commands against temporary copies and keep the source build unchanged.
