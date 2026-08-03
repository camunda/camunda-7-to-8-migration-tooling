/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.cli;

import static java.nio.file.StandardCopyOption.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConvertLocalCommandTest {
  private void setupDir(String filename, File tempDir) {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(filename)) {
      Files.copy(
          Objects.requireNonNull(in), new File(tempDir, filename).toPath(), REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldConvert(@TempDir File tempDir) {
    setupDir("c7.bpmn", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    Integer call = command.call();
    assertEquals(0, call);
  }

  @Test
  public void shouldConvertDmn(@TempDir File tempDir) {
    setupDir("first.dmn", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    Integer call = command.call();
    assertEquals(0, call);
    assertThat(tempDir.listFiles()).hasSize(2);
    assertThat(tempDir.listFiles())
        .satisfiesExactly(
            f -> f.getName().equals("first.dmn"),
            f -> f.getName().equals("converted-c8-first.dmn"));
  }

  @Test
  public void shouldConvertLegacy(@TempDir File tempDir) {
    setupDir("c7.bpmn20.xml", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    Integer call = command.call();
    assertEquals(0, call);
  }

  @Test
  public void shouldNotConvert(@TempDir File tempDir) {
    setupDir("c8.bpmn", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    Integer call = command.call();
    assertEquals(1, call);
  }

  @Test
  void shouldCreateCsv(@TempDir File tempDir) {
    setupDir("c7.bpmn", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.csv = true;
    command.file = tempDir;
    Integer call = command.call();
    assertEquals(0, call);
    assertThat(tempDir.listFiles())
        .hasSize(3)
        .anyMatch(file -> file.getName().equals("c7.bpmn"))
        .anyMatch(file -> file.getName().equals("converted-c8-c7.bpmn"))
        .anyMatch(file -> file.getName().equals("analysis-results.csv"));
  }

  @Test
  void shouldNotCreateCsv(@TempDir File tempDir) {
    setupDir("c7.bpmn", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    Integer call = command.call();
    assertEquals(0, call);
    assertThat(tempDir.listFiles())
        .hasSize(2)
        .anyMatch(file -> file.getName().equals("c7.bpmn"))
        .anyMatch(file -> file.getName().equals("converted-c8-c7.bpmn"));
  }

  @Test
  void shouldNotCreateConvertedDiagrams(@TempDir File tempDir) {
    setupDir("c7.bpmn", tempDir);
    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    command.check = true;
    Integer call = command.call();
    assertEquals(0, call);
    assertThat(tempDir.listFiles()).hasSize(1).anyMatch(file -> file.getName().equals("c7.bpmn"));
  }

  @Test
  void shouldSkipNestedDirectoriesWhenNotRecursive(@TempDir File tempDir) throws IOException {
    setupDir("c7.bpmn", tempDir);
    File nestedDir = new File(tempDir, "nested");
    assertThat(nestedDir.mkdirs()).isTrue();
    Path nestedSource = new File(tempDir, "c7.bpmn").toPath();
    Path nestedTarget = new File(nestedDir, "nested.bpmn").toPath();
    Files.copy(nestedSource, nestedTarget, REPLACE_EXISTING);

    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;
    command.notRecursive = true;

    Integer call = command.call();

    assertEquals(0, call);
    assertThat(new File(tempDir, "converted-c8-c7.bpmn")).exists();
    assertThat(new File(nestedDir, "converted-c8-nested.bpmn")).doesNotExist();
  }

  @Test
  void shouldProcessNestedDirectoriesByDefault(@TempDir File tempDir) throws IOException {
    setupDir("c7.bpmn", tempDir);
    File nestedDir = new File(tempDir, "nested");
    assertThat(nestedDir.mkdirs()).isTrue();
    Path nestedSource = new File(tempDir, "c7.bpmn").toPath();
    Path nestedTarget = new File(nestedDir, "nested.bpmn").toPath();
    Files.copy(nestedSource, nestedTarget, REPLACE_EXISTING);

    ConvertLocalCommand command = new ConvertLocalCommand();
    command.file = tempDir;

    Integer call = command.call();

    assertEquals(0, call);
    assertThat(new File(tempDir, "converted-c8-c7.bpmn")).exists();
    assertThat(new File(nestedDir, "converted-c8-nested.bpmn")).exists();
  }
}
