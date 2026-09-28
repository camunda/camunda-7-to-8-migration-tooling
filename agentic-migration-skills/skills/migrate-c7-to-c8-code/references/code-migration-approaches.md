# Code Migration Approaches (Part A)

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## Select a code approach

When a representative comparison is practical, compare both approaches on representative classes.
One class does not predict the rest of the project.

| When | Choose | What to expect |
|---|---|---|
| Semantic, mixed delegate/client, or complex code with a capable model | AI only | The skill applies patterns directly. Model capability affects the result. |
| Repeated, supported, primarily syntactic code | OpenRewrite + AI | Expect scaffolding, TODOs, cleanup, and behavioral validation. |
| A deterministic first diff helps review | OpenRewrite + AI | Validate behavior after cleanup. |
| OpenRewrite cannot run | AI only | The skill migrates from source and patterns. Review every change. |

| Recipe role | Scope |
|---|---|
| Helps | Repeated, supported, primarily syntactic Java transformations. |
| Pair with AI review | Semantic or mixed delegate/client code needs API and business-behavior context. |
| Still needs a team decision | Domain behavior, eventual consistency, transaction boundaries, architectural separation, and validation. |

## Approach A - OpenRewrite + AI

Use this approach for repeated, supported, primarily syntactic transformations or a deterministic
first diff.

### Delegate transaction gate

Before `REWRITE_COMMAND`, the skill runs the transaction and security gate in item 3 of
`code-transform-checklist.md` for every C7 JavaDelegate.
When the gate blocks migration or an open item lacks a user decision, the skill stops OpenRewrite
and asks the user for the required evidence or decision.
The skill reruns the gate after the user supplies evidence or decisions.
The skill runs `REWRITE_COMMAND` only after every gate passes or every open item has a recorded user
decision in `MIGRATION_REPORT.md`.

### Run OpenRewrite

Use the latest recipe version in the target minor:

| Camunda target | Recipe version |
|---|---|
| 8.8 | 0.2.x |
| 8.9 or 8.10 | 0.3.x |

Resolve the latest stable `rewrite-maven-plugin` version from
https://repo.maven.apache.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/maven-metadata.xml.
Exclude snapshots and pre-releases.

If the OpenRewrite plugin is not already in the build file, add it:

#### Maven - add to pom.xml:

```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>REWRITE_VERSION</version>
  <configuration>
    <activeRecipes>
      <recipe>io.camunda.migration.code.recipes.AllClientRecipes</recipe>
      <recipe>io.camunda.migration.code.recipes.AllDelegateRecipes</recipe>
      <recipe>io.camunda.migration.code.recipes.AllExternalWorkerRecipes</recipe>
    </activeRecipes>
    <skipMavenParsing>false</skipMavenParsing>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>io.camunda</groupId>
      <artifactId>camunda-7-to-8-code-conversion-recipes</artifactId>
      <version>RECIPES_VERSION</version>
    </dependency>
  </dependencies>
</plugin>
```

#### Gradle - add to build.gradle:

```groovy
plugins {
    id("org.openrewrite.rewrite") version "REWRITE_VERSION"
}
dependencies {
    rewrite("io.camunda:camunda-7-to-8-code-conversion-recipes:RECIPES_VERSION")
}
rewrite {
    activeRecipe("io.camunda.migration.code.recipes.AllClientRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllDelegateRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllExternalWorkerRecipes")
}
```

Set `REWRITE_COMMAND` to the matching build command:

| Build tool | `REWRITE_COMMAND` |
|---|---|
| Maven | `mvn rewrite:run` |
| Gradle on macOS/Linux | `./gradlew rewriteRun` |
| Gradle in Windows PowerShell | `.\gradlew.bat rewriteRun` |
| Gradle in Windows cmd | `gradlew.bat rewriteRun` |

### Java compatibility and Spotless

1. Run `java -version` from `PATH`, capture stderr, and record the major version. Show the executable:
   `command -v java` on macOS/Linux, `Get-Command java` in PowerShell, or `where java` in Windows
   Command Prompt.
   - The recipe module supports Java 21-25 (`[21,26)`). Check the project's OpenRewrite
     configuration first for a narrower range.
   - If Java is missing or outside that range, then ask for a JDK home that contains `bin/java`.
     Never install Java or change the user's system configuration.
   - Validate the supplied home with its `bin/java` (Windows: `bin/java.exe`) and `-version`.
     Reject a stale path, JRE-only directory, missing `bin/javac` (Windows: `bin/javac.exe`), or
     incompatible version.
   - When several compatible homes exist, use the lowest version. Prefer 21, then 22, 23, 24, or
     25. (SHOULD)
   - Set `JAVA_HOME` and prepend its `bin` directory to `PATH` for this invocation only. Never use
     an unvalidated Java executable.

2. Check the build files for a Spotless configuration.

3. Where Spotless is present and the selected Java major version is at least 17, run OpenRewrite
   with these JVM flags:
     - `--add-opens=java.base/java.lang=ALL-UNNAMED`
     - `--add-opens=java.base/java.util=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED`
   - Where the build uses Maven and `.mvn` exists, append the flags temporarily to
     `.mvn/jvm.config` and preserve its content. (SHOULD)
   - Where a Maven build has no `.mvn` directory, use `JAVA_TOOL_OPTIONS` for the
     `REWRITE_COMMAND` invocation.
   - Where the build uses Gradle, use `JAVA_TOOL_OPTIONS` for the `REWRITE_COMMAND` invocation.
     Do not add repository configuration for this temporary step.
   - Where the skill used a temporary `.mvn/jvm.config`, restore its previous content, or remove it when
     this step created it, whether `REWRITE_COMMAND` succeeds or fails.
   - Do not stage or commit the temporary changes.
   - If Spotless still fails in a Maven project, then ask whether to skip it or switch to another
     compatible JDK. Offer `mvn rewrite:run -Dspotless.skip=true` as the skip command.
   - If Spotless still fails in a Gradle project, then ask the user to choose another compatible JDK
     or the project's documented Spotless bypass. Never use a Maven command in a Gradle project.

4. Otherwise, run `REWRITE_COMMAND` directly.

### AI cleanup after OpenRewrite

Before AI cleanup, compare each generated `@JobWorker` with its source. Confirm its business logic,
inputs, outputs, exception behavior, and job type. Do not delete or rename source logic until this
comparison passes. Successful compilation does not confirm behavior.

Ask whether to commit the OpenRewrite result before cleanup. Then ask whether to run AI cleanup.
Proceed only on YES. Load the pattern catalog (see references/pattern-catalog-sources.md), then:

- Apply the **OpenRewrite output: de-recipe cleanup** section to every generated `@JobWorker`
  method. Use the concrete examples in `30-glue-code/idiomatic-job-worker-cleanup.md`.
- Resolve all `// TODO` comments it inserted, and fix compile errors.
- Apply checklist items 1 (dependencies/configuration), 5 (listeners), 6 (tests), 7 (JUEL), and 8
  (generated-form dependencies). Apply uncovered parts of item 2 (client code).

---

## Approach B - AI Only (AI-first)

Use this approach for the AI-only cases in the selection table. It avoids recipe artifacts, but model
capability affects the result. Apply the same behavior and semantic validation as the recipe-assisted
path.

Load the pattern catalog (see references/pattern-catalog-sources.md). Work Transform checklist items
1-8 in order. Confirm each item before the next.

---

## Approach C - Assessment Only

Write a code assessment table that includes:
- Per-file and total effort estimates
- Recipe coverage and remaining AI/manual work
- Recommended approach based on code shape, model capability, and review needs
- Where recipes help, pair with AI review, or still need a team decision
- Known risks: multi-instance listeners, custom batches, or IdentityService/FormService usage
- The Data Migrator scope: runtime, history, and identity data

Write the full report to `MIGRATION_REPORT.md` in the confirmed project root. Make no code changes.
