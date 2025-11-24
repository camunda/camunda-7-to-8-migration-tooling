/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.config.property.C8Properties;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.AutoDeployer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DeploymentResourcesTest {

  protected AutoDeployer autoDeployer;
  protected MigratorProperties migratorProperties;

  @TempDir
  protected Path tempDir;

  @BeforeEach
  public void setUp() throws Exception {
    autoDeployer = new AutoDeployer();
    migratorProperties = new MigratorProperties();

    // Use reflection to set the protected field
    Field field = AutoDeployer.class.getDeclaredField("migratorProperties");
    field.setAccessible(true);
    field.set(autoDeployer, migratorProperties);
  }

  @Test
  public void shouldReturnEmptySetWhenC8PropertiesIsNull() {
    // given: C8 properties is null
    migratorProperties.setC8(null);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return empty set
    assertThat(resources).isEmpty();
  }

  @Test
  public void shouldReturnEmptySetWhenDeploymentDirIsNull() {
    // given: deployment directory is null
    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(null);
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return empty set
    assertThat(resources).isEmpty();
  }

  @Test
  public void shouldReturnEmptySetWhenDeploymentDirIsEmpty() {
    // given: deployment directory is empty string
    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir("");
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return empty set
    assertThat(resources).isEmpty();
  }

  @Test
  public void shouldReturnEmptySetWhenDeploymentDirIsBlank() {
    // given: deployment directory is blank string
    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir("   ");
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return empty set
    assertThat(resources).isEmpty();
  }

  @Test
  public void shouldReturnEmptySetWhenDeploymentDirIsEmptyDirectory() {
    // given: deployment directory exists but is empty
    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return empty set
    assertThat(resources).isEmpty();
  }

  @Test
  public void shouldReturnSingleFileWhenOneFileExists() throws IOException {
    // given: deployment directory with one file
    Path bpmnFile = tempDir.resolve("process.bpmn");
    Files.writeString(bpmnFile, "<bpmn></bpmn>");

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return the file
    assertThat(resources).hasSize(1);
    assertThat(resources).contains(bpmnFile);
  }

  @Test
  public void shouldReturnMultipleFilesWhenMultipleFilesExist() throws IOException {
    // given: deployment directory with multiple files
    Path bpmnFile = tempDir.resolve("process.bpmn");
    Files.writeString(bpmnFile, "<bpmn></bpmn>");

    Path dmnFile = tempDir.resolve("decision.dmn");
    Files.writeString(dmnFile, "<dmn></dmn>");

    Path formFile = tempDir.resolve("form.form");
    Files.writeString(formFile, "{}");

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return all files
    assertThat(resources).hasSize(3);
    assertThat(resources).containsExactlyInAnyOrder(bpmnFile, dmnFile, formFile);
  }

  @Test
  public void shouldIncludeFilesInSubdirectories() throws IOException {
    // given: deployment directory with files in subdirectories
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    Path rootFile = tempDir.resolve("root.bpmn");
    Files.writeString(rootFile, "<bpmn></bpmn>");

    Path subFile = subDir.resolve("sub.bpmn");
    Files.writeString(subFile, "<bpmn></bpmn>");

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return files from both root and subdirectory
    assertThat(resources).hasSize(2);
    assertThat(resources).containsExactlyInAnyOrder(rootFile, subFile);
  }

  @Test
  public void shouldExcludeDirectories() throws IOException {
    // given: deployment directory with files and subdirectories
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    Path file = tempDir.resolve("process.bpmn");
    Files.writeString(file, "<bpmn></bpmn>");

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should only return the file, not the directory
    assertThat(resources).hasSize(1);
    assertThat(resources).contains(file);
    assertThat(resources).doesNotContain(subDir);
  }

  @Test
  public void shouldExcludeHiddenFiles() throws IOException {
    // given: deployment directory with regular and hidden files
    Path regularFile = tempDir.resolve("process.bpmn");
    Files.writeString(regularFile, "<bpmn></bpmn>");

    Path hiddenFile = tempDir.resolve(".hiddenFile");
    Files.writeString(hiddenFile, "hidden content");

    // Set hidden attribute based on OS
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      Files.setAttribute(hiddenFile, "dos:hidden", true);
    }
    assertThat(autoDeployer.isHidden(hiddenFile)).isTrue();

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should only return the regular file, not the hidden file
    assertThat(resources).hasSize(1);
    assertThat(resources).contains(regularFile);
    assertThat(resources).doesNotContain(hiddenFile);
  }

  @Test
  public void shouldExcludeHiddenFilesInSubdirectories() throws IOException {
    // given: deployment directory with hidden files in subdirectories
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    Path regularFile = subDir.resolve("process.bpmn");
    Files.writeString(regularFile, "<bpmn></bpmn>");

    Path hiddenFile = subDir.resolve(".hiddenFile");
    Files.writeString(hiddenFile, "hidden content");

    // Set hidden attribute based on OS
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      Files.setAttribute(hiddenFile, "dos:hidden", true);
    }
    assertThat(autoDeployer.isHidden(hiddenFile)).isTrue();

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should only return the regular file
    assertThat(resources).hasSize(1);
    assertThat(resources).contains(regularFile);
    assertThat(resources).doesNotContain(hiddenFile);
  }

  @Test
  public void shouldHandleDotPrefixedFilesOnUnixLikeSystems() throws IOException {
    // given: deployment directory with dot-prefixed file
    // Note: On Windows, this might not be treated as hidden unless explicitly set
    Path regularFile = tempDir.resolve("process.bpmn");
    Files.writeString(regularFile, "<bpmn></bpmn>");

    Path dotFile = tempDir.resolve(".gitignore");
    Files.writeString(dotFile, "*.class");

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: behavior depends on OS - on Unix-like systems, .gitignore is hidden
    // We just verify the regular file is included
    assertThat(resources).contains(regularFile);

    // Check if dot file is treated as hidden (OS-dependent)
    boolean dotFileIsHidden = autoDeployer.isHidden(dotFile);
    if (dotFileIsHidden) {
      assertThat(resources).hasSize(1);
      assertThat(resources).doesNotContain(dotFile);
    } else {
      assertThat(resources).hasSize(2);
      assertThat(resources).contains(dotFile);
    }
  }

  @Test
  public void shouldHandleNestedDirectoryStructure() throws IOException {
    // given: deployment directory with deeply nested structure
    Path level1 = tempDir.resolve("level1");
    Path level2 = level1.resolve("level2");
    Path level3 = level2.resolve("level3");
    Files.createDirectories(level3);

    Path file1 = tempDir.resolve("root.bpmn");
    Files.writeString(file1, "<bpmn></bpmn>");

    Path file2 = level1.resolve("level1.bpmn");
    Files.writeString(file2, "<bpmn></bpmn>");

    Path file3 = level2.resolve("level2.dmn");
    Files.writeString(file3, "<dmn></dmn>");

    Path file4 = level3.resolve("level3.form");
    Files.writeString(file4, "{}");

    C8Properties c8Properties = new C8Properties();
    c8Properties.setDeploymentDir(tempDir.toString());
    migratorProperties.setC8(c8Properties);

    // when: getting deployment resources
    Set<Path> resources = autoDeployer.getDeploymentResources();

    // then: should return all files from all levels
    assertThat(resources).hasSize(4);
    assertThat(resources).containsExactlyInAnyOrder(file1, file2, file3, file4);
  }
}

