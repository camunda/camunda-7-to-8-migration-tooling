/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.message;

import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import java.util.List;

public class MessageTemplate {
  private final Severity severity;
  private final String link;
  private final String template;
  private final List<String> variables;

  public MessageTemplate(Severity severity, String link, String template, List<String> variables) {
    this.severity = severity;
    this.link = link;
    this.template = template;
    this.variables = variables;
  }

  public String getTemplate() {
    return template;
  }

  public List<String> getVariables() {
    return variables;
  }

  public Severity getSeverity() {
    return severity;
  }

  public String getLink() {
    return link;
  }
}
