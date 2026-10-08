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

### Run OpenRewrite

Run `REWRITE_COMMAND` only after the delegate transaction and security gate in
`code-transform-checklist.md` item 3 passes for every C7 JavaDelegate, or `MIGRATION_REPORT.md`
records a user decision for every open item.

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

This Java check applies only to OpenRewrite Approach A.
It does not limit M1, E1, M2, or M3.
If no compatible code runtime exists, ask for one or select Approach B.

1. Validate the code-phase runtime with the Java runtime procedure in `SKILL.md`. Never use an
   unvalidated Java executable.
   - The recipe module supports Java 21-25 (`[21,26)`). Check the project's OpenRewrite
     configuration first for a narrower range.
   - Reject a stale path, JRE-only directory, missing `bin/javac` (Windows: `bin/javac.exe`), or
     incompatible version.

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

Ask whether to commit the OpenRewrite result before cleanup. Then ask whether to run AI cleanup.
Proceed only on YES. Then:

- Apply the **OpenRewrite output: de-recipe cleanup** section of `code-transform-checklist.md` to
  every generated `@JobWorker` method.
- Resolve all `// TODO` comments it inserted, and fix compile errors.
- Apply checklist items 1 (dependencies/configuration), 5 (listeners), 6 (tests), 7 (JUEL), 8
  (generated-form dependencies), and 9 (incident notifications). Apply uncovered parts of item 2
  (client code).

---

## Approach B - AI Only (AI-first)

Use this approach for the AI-only cases in the selection table. It avoids recipe artifacts, but model
capability affects the result. Apply the same behavior and semantic validation as the recipe-assisted
path.

Work every Transform checklist item in order.

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
