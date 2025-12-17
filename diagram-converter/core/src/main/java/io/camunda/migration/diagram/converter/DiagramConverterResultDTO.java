/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

public class DiagramConverterResultDTO {
  private String filename;
  private String elementName;
  private String elementId;
  private String elementType;
  private String severity;
  private String messageId;
  private String message;
  private String link;

  // Constructor
  public DiagramConverterResultDTO(
      String filename,
      String elementName,
      String elementId,
      String elementType,
      String severity,
      String messageId,
      String message,
      String link) {
    this.filename = filename;
    this.elementName = elementName;
    this.elementId = elementId;
    this.elementType = elementType;
    this.severity = severity;
    this.messageId = messageId;
    this.message = message;
    this.link = link;
  }

  // Getters
  public String getFilename() {
    return filename;
  }

  public String getElementName() {
    return elementName;
  }

  public String getElementId() {
    return elementId;
  }

  public String getElementType() {
    return elementType;
  }

  public String getSeverity() {
    return severity;
  }

  public String getMessageId() {
    return messageId;
  }

  public String getMessage() {
    return message;
  }

  public String getLink() {
    return link;
  }
}
