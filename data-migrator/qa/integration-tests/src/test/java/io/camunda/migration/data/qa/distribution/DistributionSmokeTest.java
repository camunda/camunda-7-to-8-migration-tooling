/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.distribution;

import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_DEPLOY_C8_RESOURCES;
import static io.camunda.migration.data.impl.logging.SchemaShutdownCleanerLogs.PERFORMING_DROP_ON_SUCCESSFUL_MIGRATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Smoke test for the ZIP distribution that validates the start script functionality.
 * This test extracts the ZIP distribution and executes the appropriate start script
 * (start.sh on Unix/Linux/macOS, start.bat on Windows) to ensure basic functionality works as expected.
 */
public class DistributionSmokeTest {

  protected static final String DEFAULT_LOG_FILE = "logs/camunda-7-to-8-data-migrator.log";

  @TempDir
  protected Path tempDir;

  protected Path extractedDistributionPath;
  protected Path startScriptPath;
  protected Process process;
  protected boolean isWindows;
  protected String startScriptName;

  @BeforeEach
  void setUp() throws IOException {
    // Detect operating system
    isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    startScriptName = isWindows ? "start.bat" : "start.sh";

    extractZipDistribution();
    makeScriptExecutable();
  }

  @AfterEach
  public void tearDown() {
    destroyProcessTree(process);
  }

  @Test
  void shouldOnlyPackageManagedJacksonVersions() throws IOException {
    var expectedVersions = Map.of(
        "com.fasterxml.jackson.core", System.getProperty("jackson2Version"),
        "tools.jackson.core", System.getProperty("jackson3Version"));
    Set<String> inspectedArtifacts = new HashSet<>();
    var application = extractedDistributionPath.resolve("internal/camunda-7-to-8-data-migrator.jar");

    try (var input = Files.newInputStream(application)) {
      assertPackagedJacksonVersions(input, application.getFileName().toString(), expectedVersions, inspectedArtifacts);
    }

    assertThat(inspectedArtifacts).contains(
        "com.fasterxml.jackson.core:jackson-core",
        "com.fasterxml.jackson.core:jackson-databind");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenInvalidFlagProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder("--runtime", "--invalid-flag");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Invalid flag: --invalid-flag");
    assertThat(output).contains("Usage: start.sh/bat");
    assertThat(output).contains("--help");
    assertThat(output).contains("--runtime");
    assertThat(output).contains("--history");
    assertThat(output).contains("--list-skipped");
    assertThat(output).contains("--retry-skipped");
  }

  @ParameterizedTest
  @ValueSource(strings = {DEFAULT_LOG_FILE, "custom logs/migrator.log"})
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldFailSinceC8DataSourceNotConfigured(String logFileName) throws Exception {
    // given
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");
    replaceConfigProperty("name: " + DEFAULT_LOG_FILE, "name: " + logFileName);

    // when
    String output = runProcessToCompletion(createProcessBuilder("--history"), 1);

    // then
    assertLoggedToConsoleAndFile(logFileName, output, " ERROR ",
        "No C8 datasource configured. Configure 'camunda.migrator.c8.datasource' to allow history migration.");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenNoFlagProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder();

    // Read the existing configuration file and set auto-dll to true
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Error: Must specify at least one migration type");
    assertThat(output).contains("Usage: start.sh/bat");
    assertThat(output).contains("--help");
    assertThat(output).contains("--runtime");
    assertThat(output).contains("--history");
    assertThat(output).contains("--list-skipped");
    assertThat(output).contains("--retry-skipped");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenHelpFlagProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder("--help");

    // Read the existing configuration file and set auto-dll to true
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).doesNotContain("Error");
    assertThat(output).contains("Usage: start.sh/bat");
    assertThat(output).contains("--help");
    assertThat(output).contains("--runtime");
    assertThat(output).contains("--history");
    assertThat(output).contains("--list-skipped");
    assertThat(output).contains("--retry-skipped");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenHelpFlagCombinedWithOtherFlags() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder("--help", "--runtime");

    // Read the existing configuration file and set auto-dll to true
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Error: The --help flag cannot be combined with other flags.");
    assertThat(output).contains("Usage: start.sh/bat");
    assertThat(output).contains("--help");
    assertThat(output).contains("--runtime");
    assertThat(output).contains("--history");
    assertThat(output).contains("--list-skipped");
    assertThat(output).contains("--retry-skipped");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenListAndRetryAreProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder("--runtime", "--list-skipped", "--retry-skipped");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Conflicting flags: --list-skipped and --retry-skipped cannot be used together");
    assertThat(output).contains("Usage: start.sh/bat");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenForceWithoutDropIsProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder("--runtime", "--force");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Invalid flag combination: --force requires --drop-schema. Use both flags together or remove --force.");
    assertThat(output).contains("Usage: start.sh/bat");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenTooManyArgumentsProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = createProcessBuilder("--runtime", "--history", "--history", "--drop-schema", "--force", "--list-skipped");

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Error: Too many arguments.");
    assertThat(output).contains("Usage: start.sh/bat");
  }

  @Test
  @Timeout(value = 240, unit = TimeUnit.SECONDS)
  void shouldAcceptValidFlags() throws Exception {
    // given
    String[][] validFlags = {
        {"--runtime"},
        {"--history"},
        {"--runtime", "--history"},
        {"--runtime", "--drop-schema"},
        {"--runtime", "--drop-schema", "--force"},
        {"--history", "--list-skipped"},
        {"--history", "--retry-skipped"}
    };

    for (String[] flag : validFlags) {
      ProcessBuilder processBuilder = createProcessBuilder(flag);
      String expected = "Starting migration with flags: " + String.join(" ", flag);

      // when
      Process currentProcess = processBuilder.start();
      try {
        // Starting the distribution repeatedly in a single test is slower on Windows CI.
        // Use a per-invocation budget that tolerates host variance while still failing deterministically.
        String output = readProcessOutputUntil(currentProcess, expected, 20, TimeUnit.SECONDS);

        // then
        assertThat(output).contains(expected);
      } finally {
        destroyProcessTree(currentProcess);
      }
    }
  }

  @Test
  void shouldHaveRequiredFilesInDistribution() {
    assertThat(extractedDistributionPath.resolve("start.sh")).exists();
    assertThat(extractedDistributionPath.resolve("start.bat")).exists();
    assertThat(extractedDistributionPath.resolve("configuration")).exists();
    assertThat(extractedDistributionPath.resolve("internal")).exists();
    assertThat(extractedDistributionPath.resolve("internal/launcher.properties")).exists();
    assertThat(extractedDistributionPath.resolve("internal/camunda-7-to-8-data-migrator.jar")).exists();
    assertThat(extractedDistributionPath.resolve("LICENSE.TXT")).exists();
    assertThat(extractedDistributionPath.resolve("NOTICE.txt")).exists();
    assertThat(extractedDistributionPath.resolve("README.txt")).exists();
  }

  @Test
  void shouldHaveExecutableStartScript() {
    if (isWindows) {
      // On Windows, .bat files are executable by default
      assertThat(startScriptPath.toFile().exists()).isTrue();
    } else {
      // On Unix systems, check executable permission
      assertThat(startScriptPath.toFile().canExecute()).isTrue();
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldTryToDeployBpmnModelFromResourcesFolder() throws Exception {
    // given
    Path resourcesDir = extractedDistributionPath.resolve("configuration/resources");
    Files.createDirectories(resourcesDir);

    // Read the existing configuration file and set auto-dll to true
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");

    String simpleBpmnModel = "<bpmn:definitions />";
    Path bpmnFile = resourcesDir.resolve("test-process.bpmn");
    Files.write(bpmnFile, simpleBpmnModel.getBytes());

    ProcessBuilder processBuilder = createProcessBuilder("--runtime");

    // when
    process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    assertThat(output).contains(FAILED_TO_DEPLOY_C8_RESOURCES);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldIgnoreHiddenFiles() throws Exception {
    // given
    Path resourcesDir = extractedDistributionPath.resolve("configuration/resources");
    Files.createDirectories(resourcesDir);

    // Read the existing configuration file and set auto-dll to true
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");

    String hiddenFileContent = "foo";
    Path hiddenFile = resourcesDir.resolve(".hiddenFile");
    Files.write(hiddenFile, hiddenFileContent.getBytes());

    ProcessBuilder processBuilder = createProcessBuilder("--runtime");

    // when
    process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    assertThat(output).doesNotContain(FAILED_TO_DEPLOY_C8_RESOURCES);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldApplyConfigurationChanges() throws Exception {
    // given
    Path configFile = extractedDistributionPath.resolve("configuration/application.yml");
    assertThat(configFile).exists();

    // Read the existing configuration file
    String originalConfig = Files.readString(configFile);

    // Modify the configuration by uncommenting and changing the page-size property
    String modifiedConfig = originalConfig
        .replace("auto-ddl: true", "auto-ddl: false");

    Files.write(configFile, modifiedConfig.getBytes());

    ProcessBuilder processBuilder = createProcessBuilder("--runtime");

    // when
    process = processBuilder.start();

    // then
    String output = readProcessOutput(process);

    // Verify the application started with our modified configuration
    assertThat(output).contains("ENGINE-03057 There are no Camunda tables in the database.");
  }

  @ParameterizedTest
  @ValueSource(strings = {DEFAULT_LOG_FILE, "custom logs/migrator.log"})
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldCreateLogFileWhenConfigured(String logFileName) throws Exception {
    // given
    replaceConfigProperty("auto-ddl: false", "auto-ddl: true");
    replaceConfigProperty("io.camunda.migration.data: WARN", "io.camunda.migration.data: INFO");
    replaceConfigProperty("name: " + DEFAULT_LOG_FILE, "name: " + logFileName);

    // when
    // Listing and cleaning up the empty local schema do not contact Camunda 8.
    String output = runProcessToCompletion(
        createProcessBuilder("--runtime", "--list-skipped", "--drop-schema"), 0);

    // then
    assertLoggedToConsoleAndFile(logFileName, output, " INFO ", PERFORMING_DROP_ON_SUCCESSFUL_MIGRATION);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldReportUnexpectedProcessExit() {
    assertThatThrownBy(() -> runProcessToCompletion(createProcessBuilder("--runtime", "--invalid-flag"), 0))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Process exit code: 1")
        .hasMessageContaining("Console output:")
        .hasMessageContaining("Invalid flag: --invalid-flag");
  }

  protected String runProcessToCompletion(ProcessBuilder processBuilder, int expectedExitCode)
      throws IOException, InterruptedException {
    Path consoleOutput = tempDir.resolve("console-output.log");
    process = processBuilder.redirectOutput(consoleOutput.toFile()).start();
    boolean exited = process.waitFor(25, TimeUnit.SECONDS);
    String output = Files.readString(consoleOutput);
    String diagnostics = "Command: %s%nProcess exit code: %s%nConsole output:%n%s".formatted(
        processBuilder.command(), exited ? process.exitValue() : "still running after 25 seconds", output);

    assertThat(exited).as(diagnostics).isTrue();
    assertThat(process.exitValue()).as(diagnostics).isEqualTo(expectedExitCode);
    return output;
  }

  protected void assertLoggedToConsoleAndFile(String logFileName, String output, String... expectedMessages)
      throws IOException {
    Path logFile = extractedDistributionPath.resolve(logFileName);
    String diagnostics = "Log file: %s%nProcess exit code: %s%nConsole output:%n%s".formatted(
        logFile, process.exitValue(), output);

    assertThat(output).as(diagnostics).contains(expectedMessages);
    assertThat(logFile).as(diagnostics).isRegularFile().isNotEmptyFile();
    assertThat(Files.readString(logFile)).as(diagnostics).contains(expectedMessages);
    if (!DEFAULT_LOG_FILE.equals(logFileName)) {
      assertThat(extractedDistributionPath.resolve(DEFAULT_LOG_FILE)).as(diagnostics).doesNotExist();
    }
  }

  protected void replaceConfigProperty(String before, String after) throws IOException {
    Path configFile = extractedDistributionPath.resolve("configuration/application.yml");
    String originalConfig = Files.readString(configFile);
    assertThat(originalConfig).as("Configuration fixture must contain the property being replaced").contains(before);
    String modifiedConfig = originalConfig.replace(before, after);
    Files.write(configFile, modifiedConfig.getBytes());
  }

  protected void extractZipDistribution() throws IOException {
    Path zipFile = findZipDistribution();
    assertThat(zipFile).exists();

    extractedDistributionPath = tempDir.resolve("extracted");
    Files.createDirectories(extractedDistributionPath);

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        Path outputPath = extractedDistributionPath.resolve(entry.getName());

        if (entry.isDirectory()) {
          Files.createDirectories(outputPath);
        } else {
          Files.createDirectories(outputPath.getParent());
          Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }

    // Find the actual distribution directory (it should be nested)
    try (var stream = Files.list(extractedDistributionPath)) {
      extractedDistributionPath = stream
          .filter(Files::isDirectory)
          .filter(path -> path.getFileName().toString().startsWith("camunda-7-to-8-data-migrator"))
          .findFirst()
          .orElse(extractedDistributionPath);
    }

    startScriptPath = extractedDistributionPath.resolve(startScriptName);
  }

  protected void assertPackagedJacksonVersions(
      InputStream input, String archivePath, Map<String, String> expectedVersions, Set<String> inspectedArtifacts)
      throws IOException {
    try (var archive = new ZipInputStream(input)) {
      ZipEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        var path = archivePath + "!/" + entry.getName();
        if (entry.getName().endsWith(".jar")) {
          assertPackagedJacksonVersions(
              new ByteArrayInputStream(archive.readAllBytes()), path, expectedVersions, inspectedArtifacts);
        } else if (entry.getName().endsWith("/pom.properties")) {
          var properties = new Properties();
          properties.load(archive);
          var groupId = properties.getProperty("groupId");
          var artifactId = properties.getProperty("artifactId");
          if (expectedVersions.containsKey(groupId)
              && ("jackson-core".equals(artifactId) || "jackson-databind".equals(artifactId))) {
            assertThat(properties.getProperty("version"))
                .as("Jackson version in %s", path)
                .isEqualTo(expectedVersions.get(groupId));
            inspectedArtifacts.add(groupId + ":" + artifactId);
          }
        }
      }
    }
  }

  protected Path findZipDistribution() {
    // Look for the ZIP file in the assembly target directory
    Path assemblyTarget = Paths.get(System.getProperty("user.dir"))
        .resolve("../../assembly/target");

    try (var stream = Files.list(assemblyTarget)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith(".zip"))
          .filter(path -> path.getFileName().toString().contains("camunda-7-to-8-data-migrator"))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("ZIP distribution not found in " + assemblyTarget));
    } catch (IOException e) {
      throw new RuntimeException("Failed to find ZIP distribution", e);
    }
  }

  protected void makeScriptExecutable() {
    if (startScriptPath.toFile().exists() && !isWindows) {
      // Only need to set executable on Unix systems
      boolean success = startScriptPath.toFile().setExecutable(true);
      if (!success) {
        throw new RuntimeException("Failed to make " + startScriptName + " executable");
      }
    }
  }

  /**
   * Creates a ProcessBuilder with the appropriate start script for the current OS
   */
  protected ProcessBuilder createProcessBuilder(String... args) {
    String[] command;

    if (isWindows) {
      // On Windows, batch files need to be executed through cmd.exe
      command = new String[args.length + 3];
      command[0] = "cmd.exe";
      command[1] = "/c";
      command[2] = startScriptName;
      System.arraycopy(args, 0, command, 3, args.length);
    } else {
      // On Unix systems, use the script directly with ./
      command = new String[args.length + 1];
      command[0] = "./" + startScriptName;
      System.arraycopy(args, 0, command, 1, args.length);
    }

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(extractedDistributionPath.toFile());
    processBuilder.redirectErrorStream(true);
    return processBuilder;
  }

  protected String readProcessOutput(Process process) throws IOException {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append(System.lineSeparator());
      }
    }
    return output.toString();
  }

  /**
   * Reads process output until the expected string is found or the timeout elapses. Returns all
   * output collected so far in either case. Uses a dedicated reader thread so blocking {@code
   * readLine()} calls do not interfere with the deadline check — this is important on Windows where
   * stream {@code ready()} is unreliable for subprocess stdout.
   */
  protected String readProcessOutputUntil(
      final Process process,
      final String expected,
      final long timeout,
      final TimeUnit unit)
      throws InterruptedException {
    final StringBuilder output = new StringBuilder();
    final Thread readerThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  synchronized (output) {
                    output.append(line).append(System.lineSeparator());
                    if (output.indexOf(expected) >= 0) {
                      return;
                    }
                  }
                }
              } catch (final IOException e) {
                // stream closed when process is destroyed — expected
              }
            });
    readerThread.setDaemon(true);
    readerThread.start();

    final long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      synchronized (output) {
        if (output.indexOf(expected) >= 0) {
          return output.toString();
        }
      }
      if (!process.isAlive()) {
        readerThread.join(1_000);
        return output.toString();
      }
      Thread.sleep(50);
    }
    readerThread.interrupt();
    return output.toString();
  }

  /**
   * Terminates a process and its entire descendant tree. On Windows, {@code cmd.exe /c start.bat}
   * spawns a child JVM that holds file locks on the extracted JARs; {@link Process#destroy()} only
   * kills the {@code cmd.exe} parent. Descendants are destroyed first, then the parent, with a
   * {@link Process#destroyForcibly()} fallback. {@link InterruptedException} from {@code waitFor}
   * restores the interrupt flag and triggers an immediate forcible kill so cleanup always completes.
   */
  protected void destroyProcessTree(final Process proc) {
    if (proc == null) {
      return;
    }
    proc.descendants().forEach(ProcessHandle::destroyForcibly);
    proc.destroy();
    try {
      if (!proc.waitFor(5, TimeUnit.SECONDS)) {
        proc.destroyForcibly();
        proc.waitFor(5, TimeUnit.SECONDS);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      proc.destroyForcibly();
    }
  }
}
