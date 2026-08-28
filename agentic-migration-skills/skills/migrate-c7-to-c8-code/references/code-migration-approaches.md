# Code Migration Approaches (Part A)

## Approach A - OpenRewrite + AI (recommended)

Before resolving versions, inspect the selected target or maintenance branch's existing configuration and verify Camunda, Spring Boot, recipe, and dependency compatibility against official Camunda documentation or Maven metadata. Do not blindly choose a newer platform version.

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
   - Combine all applicable discovery sources; a source returning no results does not prove that no JDK is installed:
     - the current `JAVA_HOME` value, if set (validate it rather than trusting it);
     - `/usr/libexec/java_home -V` on macOS (including its stderr output);
     - Homebrew keg-only JDKs under `/opt/homebrew/opt/openjdk@*/` (Apple Silicon) and `/usr/local/opt/openjdk@*/` (Intel), checking both the formula prefix and the common nested `libexec/openjdk.jdk/Contents/Home` JDK home;
     - the macOS JDK root `/Library/Java/JavaVirtualMachines/*/Contents/Home`;
     - common version-manager roots such as `~/.sdkman/candidates/java/`, `~/.jenv/versions/`, and `~/.asdf/installs/java/`;
     - platform-equivalent standard JDK roots, such as `/usr/lib/jvm/` on Linux and the Java installation directories on Windows.
   - For every candidate, resolve symlinks, deduplicate paths, require a real `<jdk-home>/bin/java`, and execute that binary to read its actual major version. Do not infer the version only from a directory name or from `java` on `PATH`; ignore stale, JRE-only, and incompatible candidates.
   - Prefer an already-installed compatible JDK over asking to install one. Use the current `JAVA_HOME` when it is compatible; otherwise choose deterministically from the discovered candidates, preferring Java 21 and then the other versions in the compatible window. Continue discovery when `JAVA_HOME` or `/usr/libexec/java_home -V` reports only incompatible versions, such as Java 17 and 26.
   - Scope `JAVA_HOME` and `PATH` to the rewrite invocation using the selected JDK (for example, `JAVA_HOME=<jdk-home>` and `<jdk-home>/bin` first on `PATH`). Do not modify shell profiles, create system-wide symlinks, or require the optional Homebrew link step.
   - Only when no compatible JDK is found by any source, ask via AskUserQuestion to install one (e.g., `brew install openjdk@21` on macOS), wait for confirmation, and run the complete discovery and validation process again afterward.

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
