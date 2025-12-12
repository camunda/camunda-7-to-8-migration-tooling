/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.cli;

public class ProcessDefinitionDiagramDto {
  private String bpmn20Xml;

  public String getBpmn20Xml() {
    return bpmn20Xml;
  }

  public void setBpmn20Xml(String bpmn20Xml) {
    this.bpmn20Xml = bpmn20Xml;
  }
}
