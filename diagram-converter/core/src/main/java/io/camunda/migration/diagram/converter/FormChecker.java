/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

/**
 * Analyzes Camunda 7 form files ({@code *.form}) and reports findings for content that needs review
 * when migrating to Camunda 8. Checking delegates to the combined form conversion pipeline so
 * expression classification and traversal stay identical for checking and conversion.
 */
public class FormChecker {

  /** Synthetic element type used for findings that apply to the form file as a whole. */
  public static final String FORM_ELEMENT_TYPE = "form";

  /**
   * Highest form-js schema version known to this converter. Forms with an older (or missing) schema
   * version are flagged for review.
   */
  public static final int LATEST_KNOWN_SCHEMA_VERSION = 18;

  private FormChecker() {}

  /**
   * Checks the given form JSON and returns the findings.
   *
   * @param filename the name of the form file, used in the result
   * @param formContent the content of a form file
   * @param properties the converter properties, providing the target platform version
   * @return the check result containing one entry per finding
   * @throws IllegalArgumentException if the content is not valid JSON or not a JSON object, or the
   *     platform version is missing or invalid
   */
  public static DiagramCheckResult check(
      String filename, String formContent, ConverterProperties properties) {
    return FormConverter.convertAndCheck(filename, formContent, properties).checkResult();
  }
}
