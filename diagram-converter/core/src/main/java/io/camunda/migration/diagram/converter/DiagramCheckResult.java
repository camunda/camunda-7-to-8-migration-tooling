/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiagramCheckResult {
  private List<ElementCheckResult> results = new ArrayList<>();
  private String filename;
  private String converterVersion;

  /**
   * Index of {@link #results} by element id, so lookups stay O(1) for large models (see issue
   * #3695). Results are appended through {@link #getResults()}, so the index catches up lazily with
   * entries added since the last lookup.
   */
  private transient Map<String, ElementCheckResult> resultsById = new HashMap<>();

  private transient List<ElementCheckResult> indexedList;
  private transient int indexedCount;

  public String getConverterVersion() {
    return converterVersion;
  }

  public void setConverterVersion(String converterVersion) {
    this.converterVersion = converterVersion;
  }

  public List<ElementCheckResult> getResults() {
    return results;
  }

  public void setResults(List<ElementCheckResult> results) {
    this.results = results;
    indexedList = null;
  }

  /**
   * Returns the first result with the given element id, or {@code null} if there is none.
   *
   * <p>Assumes results are only appended to the list returned by {@link #getResults()}; replacing
   * the list through {@link #setResults(List)} or shrinking it triggers a full re-index.
   */
  public ElementCheckResult getResult(String elementId) {
    if (elementId == null) {
      return null;
    }
    if (indexedList != results || indexedCount > results.size()) {
      resultsById = new HashMap<>();
      indexedList = results;
      indexedCount = 0;
    }
    for (; indexedCount < results.size(); indexedCount++) {
      ElementCheckResult result = results.get(indexedCount);
      if (result.getElementId() != null) {
        resultsById.putIfAbsent(result.getElementId(), result);
      }
    }
    return resultsById.get(elementId);
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  @Override
  public String toString() {
    return "DiagramCheckResult{" + "results=" + results + ", filename='" + filename + '\'' + '}';
  }

  public enum Severity {
    WARNING,
    TASK,
    REVIEW,
    INFO,
  }

  public static class ElementCheckResult {
    private List<ElementCheckMessage> messages = new ArrayList<>();
    private List<String> references = new ArrayList<>();
    private List<String> referencedBy = new ArrayList<>();
    private String elementId;
    private String elementName;
    private String elementType;

    public List<ElementCheckMessage> getMessages() {
      return messages;
    }

    public void setMessages(List<ElementCheckMessage> messages) {
      this.messages = messages;
    }

    public String getElementId() {
      return elementId;
    }

    public void setElementId(String elementId) {
      this.elementId = elementId;
    }

    public String getElementName() {
      return elementName;
    }

    public void setElementName(String elementName) {
      this.elementName = elementName;
    }

    public String getElementType() {
      return elementType;
    }

    public void setElementType(String elementType) {
      this.elementType = elementType;
    }

    public List<String> getReferences() {
      return references;
    }

    public void setReferences(List<String> references) {
      this.references = references;
    }

    public List<String> getReferencedBy() {
      return referencedBy;
    }

    public void setReferencedBy(List<String> referencedBy) {
      this.referencedBy = referencedBy;
    }

    @Override
    public String toString() {
      return "ElementCheckResult{"
          + "messages="
          + messages
          + ", references="
          + references
          + ", referencedBy="
          + referencedBy
          + ", elementId='"
          + elementId
          + '\''
          + ", elementName='"
          + elementName
          + '\''
          + ", elementType='"
          + elementType
          + '\''
          + '}';
    }
  }

  public static class ElementCheckMessage {
    private Severity severity;
    private String message;
    private String link;
    private String id;

    public Severity getSeverity() {
      return severity;
    }

    public void setSeverity(Severity severity) {
      this.severity = severity;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public String getLink() {
      return link;
    }

    public void setLink(String link) {
      this.link = link;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return "ElementCheckMessage{"
          + "severity="
          + severity
          + ", message='"
          + message
          + '\''
          + ", link='"
          + link
          + '\''
          + ", id='"
          + id
          + '\''
          + '}';
    }
  }
}
