# Code Migration Approaches (Part A)

## Approach A - OpenRewrite + AI (recommended)

### 1. Run OpenRewrite

RECIPES_VERSION by Camunda target (use latest from these minor versions):
- 8.8: 0.2.x
- 8.9 and 8.10: 0.3.x

REWRITE_VERSION: Resolve latest released version via WebFetch:
- rewrite-maven-plugin: https://repo.maven.apache.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/maven-metadata.xml
- Select highest stable version from versions, excluding snapshots and pre-releases.

Check if the OpenRewrite plugin is already in the build file. If not, add it:

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

Run platform-appropriate command:
- macOS/Linux: `./gradlew rewriteRun`
- Windows PowerShell: `.\gradlew.bat rewriteRun`
- Windows cmd: `gradlew.bat rewriteRun`

### Java Compatibility for OpenRewrite

Before running, check Java runtime compatibility:

1. Detect installed JDKs and pick one compatible with the selected OpenRewrite + recipe versions.
   - For rewrite-maven-plugin 6.12.0 + camunda-7-to-8-code-conversion-recipes 0.3.x, the known-safe window is Java 21-23 (recipes require Java 21+, while this rewrite plugin line can fail on Java 24+).
   - Detect installed JDKs with a platform-appropriate method (e.g., `/usr/libexec/java_home -V` on macOS), then choose a compatible one and scope JAVA_HOME/PATH to that JDK only for the rewrite step.
   - If no compatible JDK is installed, ask via AskUserQuestion to install one (e.g., `brew install openjdk@21` on macOS), wait for confirmation, then re-detect.

2. Inspect the build files to determine whether Spotless is configured.

3. If Spotless is present AND selected Java major version >= 17:
   - Run the OpenRewrite goal with JVM flags Spotless needs on Java 17+:
     - `--add-opens=java.base/java.lang=ALL-UNNAMED`
     - `--add-opens=java.base/java.util=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED`
     - `--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED`
   - Apply flags using a portable Maven JVM option mechanism:
     - If .mvn directory exists, prefer appending to .mvn/jvm.config temporarily while preserving existing content.
     - Otherwise, use JAVA_TOOL_OPTIONS rather than creating repository configuration solely for this temporary step.
     - Arrange cleanup so it runs whether mvn rewrite:run succeeds or fails: restore previous .mvn/jvm.config content or remove if this step created it.
     - Do not stage or commit the temporary changes.
   - If this still fails with a Spotless error, ask: "Spotless is incompatible with your current Java version. Would you like to skip it (`mvn rewrite:run -Dspotless.skip=true`) or switch to another JDK within the compatibility window?"

4. If Spotless is not present, or the selected Java major version < 17, run `mvn rewrite:run` directly.

### 2. AI Cleanup After OpenRewrite

Ask the user whether to run AI cleanup; proceed only on YES. Load the pattern catalog (see references/pattern-catalog-sources.md), then work the Transform checklist for what OpenRewrite left:

- Resolve all `// TODO` comments it inserted, and fix compile errors.
- Apply checklist items 1 (deps/config), 5 (listeners), 6 (tests), 7 (JUEL), and any 2 (client code) the recipes did not cover.

Before AI cleanup, ask whether to commit the OpenRewrite result.

---

## Approach B - AI Only

Load the pattern catalog (see references/pattern-catalog-sources.md), then work the full Transform checklist (items 1-7) in order, confirming each before the next.

Use this when:
- Non-Maven/Gradle builds
- Restricted environments where OpenRewrite cannot run
- User wants to review every change individually

---

## Approach C - Assessment Only

Present the code assessment table with additional detail:
- Per-file effort estimate (hours)
- Total estimated effort
- Which files OpenRewrite can handle automatically vs. require manual AI work
- Recommended approach (A or B) based on codebase size and complexity
- Known risks or blockers (multi-instance listener pattern, custom batches, IdentityService/FormService usage)
- Data migration scope note (Data Migrator: runtime / history / identity)

Write the full report to `MIGRATION_REPORT.md` in the confirmed project root. Make no code changes.
