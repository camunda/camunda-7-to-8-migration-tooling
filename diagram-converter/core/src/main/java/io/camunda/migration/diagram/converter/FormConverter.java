/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import java.io.IOException;

/**
 * Converts Camunda 7 form files ({@code *.form}) to Camunda 8. Platform metadata is updated and
 * simple JUEL variable references in component properties are transformed to FEEL. Findings are
 * collected during the same traversal used for conversion.
 */
public class FormConverter {

  public static final String FILE_EXTENSION = ".form";
  public static final String TARGET_EXECUTION_PLATFORM = "Camunda Cloud";

  private static final String EXECUTION_PLATFORM_FIELD = "executionPlatform";
  private static final String EXECUTION_PLATFORM_VERSION_FIELD = "executionPlatformVersion";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final ObjectWriter WRITER = createWriter();

  private FormConverter() {}

  public static boolean isFormFile(String fileName) {
    return fileName != null && fileName.endsWith(FILE_EXTENSION);
  }

  /**
   * Converts the given form JSON to the target platform described by the converter properties.
   *
   * @param formContent the content of a Camunda 7 form file
   * @param properties the converter properties, providing the target platform version
   * @return the converted form JSON with updated platform metadata and safe expression
   *     transformations
   * @throws IllegalArgumentException if the content is not valid JSON or not a JSON object, or the
   *     platform version is missing or invalid
   */
  public static String convert(String formContent, ConverterProperties properties) {
    return convertAndCheck(FormChecker.FORM_ELEMENT_TYPE + FILE_EXTENSION, formContent, properties)
        .convertedForm();
  }

  /**
   * Converts and checks the given form JSON in one parse and component traversal.
   *
   * @param filename the name of the form file, used in the check result
   * @param formContent the content of a Camunda 7 form file
   * @param properties the converter properties, providing the target platform version
   * @return the converted form and findings collected during conversion
   * @throws IllegalArgumentException if the content is not valid JSON or not a JSON object, or the
   *     platform version is missing or invalid
   */
  public static FormConversionResult convertAndCheck(
      String filename, String formContent, ConverterProperties properties) {
    String targetVersion = resolveTargetVersion(properties);
    JsonNode root = readTree(formContent);
    if (!(root instanceof ObjectNode form)) {
      throw new IllegalArgumentException(
          "Form content must be a JSON object, but was: " + root.getNodeType());
    }
    DiagramCheckResult checkResult = FormProcessor.process(filename, form);
    form.put(EXECUTION_PLATFORM_FIELD, TARGET_EXECUTION_PLATFORM);
    form.put(EXECUTION_PLATFORM_VERSION_FIELD, targetVersion);
    try {
      return new FormConversionResult(WRITER.writeValueAsString(form), checkResult);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Error while writing converted form", e);
    }
  }

  private static JsonNode readTree(String formContent) {
    if (formContent == null || formContent.isBlank()) {
      throw new IllegalArgumentException("Form content must be a JSON object, but was empty");
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(formContent);
      if (root == null || root.isMissingNode()) {
        throw new IllegalArgumentException("Form content must be a JSON object, but was empty");
      }
      return root;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Form content is not valid JSON: " + e.getMessage(), e);
    }
  }

  private static String resolveTargetVersion(ConverterProperties properties) {
    String platformVersion = properties.getPlatformVersion();
    if (platformVersion == null || platformVersion.isBlank()) {
      throw new IllegalArgumentException("No platform version configured");
    }
    try {
      return SemanticVersion.parse(platformVersion).getPatchZeroVersion();
    } catch (IllegalStateException e) {
      // user-provided platform versions are client input, so surface parse failures as such
      throw new IllegalArgumentException("Not a valid platform version: " + platformVersion, e);
    }
  }

  private static ObjectWriter createWriter() {
    DefaultPrettyPrinter prettyPrinter = new FormPrettyPrinter();
    DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
    prettyPrinter.indentObjectsWith(indenter);
    prettyPrinter.indentArraysWith(indenter);
    return OBJECT_MAPPER.writer(prettyPrinter);
  }

  private static class FormPrettyPrinter extends DefaultPrettyPrinter {

    FormPrettyPrinter() {
      super();
    }

    FormPrettyPrinter(FormPrettyPrinter base) {
      super(base);
    }

    @Override
    public FormPrettyPrinter createInstance() {
      return new FormPrettyPrinter(this);
    }

    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator g) throws IOException {
      // use the JSON-conventional ": " separator instead of the " : " default
      g.writeRaw(": ");
    }
  }
}
