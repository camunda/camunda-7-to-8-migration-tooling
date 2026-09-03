/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import org.apache.commons.lang3.StringUtils;

/**
 * Classification of a Camunda 7 {@code camunda:formKey} value.
 *
 * <p>Camunda 7 form keys follow the structure {@code FORM-TYPE:LOCATION:FORM.NAME}. The form type
 * is either {@code embedded} or {@code camunda-forms}; a value without a known form type is
 * rendered as an External Task Form by a custom application.
 *
 * <p>Each form type needs a different manual migration to Camunda 8, so the converter reports them
 * as distinct findings instead of one generic form key finding.
 *
 * @see <a href="https://docs.camunda.org/manual/latest/user-guide/task-forms/">Camunda 7 Task
 *     Forms</a>
 */
public enum FormKeyType {
  /** A Camunda Form ({@code .form}) referenced by {@code camunda-forms:...}. */
  CAMUNDA_FORM,
  /**
   * Custom HTML/JavaScript rendered inside Camunda 7 Tasklist, referenced by {@code embedded:...}.
   */
  EMBEDDED,
  /** A form key built at runtime from a Camunda 7 expression, so its form type is unknown. */
  EXPRESSION,
  /** A URL or application specific key resolved by a custom application. */
  EXTERNAL;

  private static final String EMBEDDED_PREFIX = "embedded:";
  private static final String CAMUNDA_FORMS_PREFIX = "camunda-forms:";

  /**
   * Classifies a raw {@code camunda:formKey} value.
   *
   * <p>A known form type prefix wins over an expression: {@code embedded:app:forms/${id}.html} is
   * still an embedded form, only its location is dynamic. A form key is only classified as {@link
   * #EXPRESSION} when no form type can be derived, which includes a fully dynamic key such as
   * {@code ${formKey}}.
   *
   * <p>The value is matched exactly, without trimming or case folding, because Camunda 7 resolves
   * the form type by exact string comparison. A value such as {@code " embedded:app:form.html"} was
   * an External Task Form in Camunda 7 and is classified as {@link #EXTERNAL} here too.
   *
   * @param formKey the raw attribute value, may be {@code null}
   * @return the form type the Camunda 7 engine would have resolved, never {@code null}
   */
  public static FormKeyType of(String formKey) {
    String value = StringUtils.defaultString(formKey);
    if (value.startsWith(EMBEDDED_PREFIX)) {
      return EMBEDDED;
    }
    if (value.startsWith(CAMUNDA_FORMS_PREFIX)) {
      return CAMUNDA_FORM;
    }
    if (value.contains("${") || value.contains("#{")) {
      return EXPRESSION;
    }
    return EXTERNAL;
  }
}
