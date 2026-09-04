# Code Migration Approaches (Part A)

## Approach A - OpenRewrite + AI (recommended)

### 1. Run OpenRewrite

RECIPES_VERSION by Camunda target (use latest from these minor versions):
- 8.8: 0.2.x
- 8.9 and 8.10: 0.3.x

REWRITE_VERSION: resolve the latest released version via WebFetch:
- rewrite-maven-plugin: https://repo.maven.apache.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/maven-metadata.xml
- Select the highest stable version, excluding snapshots and pre-releases.

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
rewrite {
    activeRecipe("io.camunda.migration.code.recipes.AllClientRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllDelegateRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllExternalWorkerRecipes")
}
```

Run the platform-appropriate command:
- macOS/Linux: `./gradlew rewriteRun`
- Windows PowerShell: `.\gradlew.bat rewriteRun`
- Windows cmd: `gradlew.bat rewriteRun`

### Java Compatibility for OpenRewrite

1. Run `java -version` from `PATH` (capture stderr) and record the actual major version. Show which executable runs: `command -v java` on macOS/Linux, `Get-Command java` in PowerShell, or `where java` in Windows Command Prompt.
   - The recipe module supports Java 21-25 (`[21,26)`). Check the selected project's OpenRewrite configuration for a stricter requirement first.
   - If `java` is missing or outside that window, ask via AskUserQuestion for an alternate JDK home (the directory containing `bin/java`). Never install Java or change the user's system configuration automatically.
   - Validate the supplied home: run its `bin/java` (Windows: `bin/java.exe`) with `-version`, capture stderr, and check the actual major version. Reject a missing `bin/javac` (Windows: `bin/javac.exe`), a stale path, a JRE-only directory, or an incompatible version.
   - If multiple validated compatible homes exist, choose the lowest compatible major version to keep runs reproducible: prefer 21, then 22, 23, 24, 25.
   - Use the validated home only for this rewrite invocation: set `JAVA_HOME` and prepend its `bin` directory to `PATH`. Never use an unvalidated `java` on `PATH`.

2. Check the build files for a Spotless configuration.

3. If Spotless is present AND the selected Java major version >= 17:
   - Run the OpenRewrite goal with the JVM flags Spotless needs on Java 17+:
     - `--add-opens=java.base/java.lang=ALL-UNNAMED`
     - `--add-opens=java.base/java.util=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED`
   - Apply the flags via a portable Maven JVM option mechanism:
     - If a `.mvn` directory exists, append to `.mvn/jvm.config` temporarily and preserve existing content (SHOULD).
     - Otherwise use `JAVA_TOOL_OPTIONS`, not new repository configuration for this temporary step.
     - Arrange cleanup to run whether `mvn rewrite:run` succeeds or fails: restore the previous `.mvn/jvm.config` content, or remove it if this step created it.
     - Do not stage or commit the temporary changes.
   - If this still fails with a Spotless error, ask: "Spotless is incompatible with your current Java version. Would you like to skip it (`mvn rewrite:run -Dspotless.skip=true`) or switch to another JDK within the compatibility window?"

4. If Spotless is not present, or the selected Java major version < 17, run `mvn rewrite:run` directly.

### 2. AI Cleanup After OpenRewrite

Ask the user whether to run AI cleanup. Proceed only on YES. Load the pattern catalog (see references/pattern-catalog-sources.md), then work the Transform checklist for what OpenRewrite left:

- Resolve all `// TODO` comments it inserted, and fix compile errors.
- Apply checklist items 1 (deps/config), 5 (listeners), 6 (tests), 7 (JUEL), 8 (generated-form dependencies), and any 2 (client code) the recipes did not cover.

Before AI cleanup, ask whether to commit the OpenRewrite result.

---

## Approach B - AI Only

Load the pattern catalog (see references/pattern-catalog-sources.md), then work the full Transform checklist (items 1-8) in order, confirming each before the next.

Use this when:
- Non-Maven/Gradle builds
- Restricted environments where OpenRewrite cannot run
- User wants to review every change individually

---

## Approach C - Assessment Only

Present the code assessment table with extra detail:
- Per-file effort estimate (hours)
- Total estimated effort
- Which files OpenRewrite can handle automatically vs. require manual AI work
- Recommended approach (A or B) based on codebase size and complexity
- Known risks or blockers (multi-instance listener pattern, custom batches, IdentityService/FormService usage)
- Data migration scope note (Data Migrator: runtime / history / identity)

Write the full report to `MIGRATION_REPORT.md` in the confirmed project root. Make no code changes.
