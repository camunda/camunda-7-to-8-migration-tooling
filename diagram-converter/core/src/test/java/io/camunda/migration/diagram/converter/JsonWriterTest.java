/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckMessage;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckResult;
import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JsonWriterTest {
  private static final String FILENAME = "mock-process.bpmn";
  private static final String LINK = "https://www.example.com";
  private static final String MESSAGE_ID = "test-message";
  private static final String MESSAGE = "Test message";
  private static final String ELEMENT_ID = "abc.123";
  private static final String ELEMENT_NAME = "Example;Name";
  private static final String ELEMENT_TYPE = "userTask";
  private static final Severity SEVERITY = Severity.TASK;
  private static final DiagramConverter SERVICE = DiagramConverterFactory.getInstance().get();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void shouldCreateValidJson() throws IOException {
    StringWriter writer = new StringWriter();
    SERVICE.writeJsonFile(mockResults(), writer);

    JsonNode root = OBJECT_MAPPER.readTree(writer.toString());
    assertThat(root.isArray()).isTrue();
    assertThat(root).hasSize(1);
    JsonNode entry = root.get(0);
    assertThat(entry.get("filename").asText()).isEqualTo(FILENAME);
    assertThat(entry.get("elementName").asText()).isEqualTo(ELEMENT_NAME);
    assertThat(entry.get("elementId").asText()).isEqualTo(ELEMENT_ID);
    assertThat(entry.get("elementType").asText()).isEqualTo(ELEMENT_TYPE);
    assertThat(entry.get("severity").asText()).isEqualTo(SEVERITY.name());
    assertThat(entry.get("messageId").asText()).isEqualTo(MESSAGE_ID);
    assertThat(entry.get("message").asText()).isEqualTo(MESSAGE);
    assertThat(entry.get("link").asText()).isEqualTo(LINK);
  }

  @Test
  public void shouldSerializeAllFindings() throws IOException {
    DiagramCheckResult result = mockDiagramResult();
    result.getResults().getFirst().getMessages().add(mockMessage());

    StringWriter writer = new StringWriter();
    SERVICE.writeJsonFile(List.of(result), writer);

    JsonNode root = OBJECT_MAPPER.readTree(writer.toString());
    assertThat(root).hasSize(2);
  }

  @Test
  public void shouldPreserveSpecialCharactersVerbatim() throws IOException {
    // report messages routinely contain ';', quotes and JUEL/FEEL expressions - content that
    // corrupts rows when a report format is parsed with naive string splitting
    String trickyMessage =
        "Method invocation is not possible in FEEL: ${execution.getVariable(\"a\").size()}; use a different expression";
    DiagramCheckResult result = mockDiagramResult();
    result.getResults().getFirst().getMessages().getFirst().setMessage(trickyMessage);

    StringWriter writer = new StringWriter();
    SERVICE.writeJsonFile(List.of(result), writer);

    JsonNode root = OBJECT_MAPPER.readTree(writer.toString());
    assertThat(root.get(0).get("message").asText()).isEqualTo(trickyMessage);
  }

  @Test
  public void shouldMatchCsvRowContent() throws IOException {
    List<DiagramCheckResult> results = mockResults();

    StringWriter jsonWriter = new StringWriter();
    SERVICE.writeJsonFile(results, jsonWriter);
    JsonNode entry = OBJECT_MAPPER.readTree(jsonWriter.toString()).get(0);

    StringWriter csvWriter = new StringWriter();
    SERVICE.writeCsvFile(results, csvWriter);
    String csv = csvWriter.toString();

    assertThat(csv).contains(entry.get("filename").asText());
    assertThat(csv).contains(entry.get("elementName").asText());
    assertThat(csv).contains(entry.get("elementId").asText());
    assertThat(csv).contains(entry.get("elementType").asText());
    assertThat(csv).contains(entry.get("severity").asText());
    assertThat(csv).contains(entry.get("messageId").asText());
    assertThat(csv).contains(entry.get("message").asText());
    assertThat(csv).contains(entry.get("link").asText());
  }

  private List<DiagramCheckResult> mockResults() {
    List<DiagramCheckResult> results = new ArrayList<>();
    results.add(mockDiagramResult());
    return results;
  }

  private DiagramCheckResult mockDiagramResult() {
    DiagramCheckResult result = new DiagramCheckResult();
    result.setFilename(FILENAME);
    result.getResults().add(mockElementResult());
    return result;
  }

  private ElementCheckResult mockElementResult() {
    ElementCheckResult result = new ElementCheckResult();
    result.setElementId(ELEMENT_ID);
    result.setElementName(ELEMENT_NAME);
    result.setElementType(ELEMENT_TYPE);
    result.getMessages().add(mockMessage());
    return result;
  }

  private ElementCheckMessage mockMessage() {
    ElementCheckMessage message = new ElementCheckMessage();
    message.setSeverity(SEVERITY);
    message.setMessage(MESSAGE);
    message.setLink(LINK);
    message.setId(MESSAGE_ID);
    return message;
  }
}
