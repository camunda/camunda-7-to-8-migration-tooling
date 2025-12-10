/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter;

import io.camunda.migration.model.converter.DiagramCheckResult.ElementCheckResult;
import io.camunda.migration.model.converter.convertible.Convertible;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.model.xml.instance.DomElement;

public class DiagramCheckContext {
  private final Set<DomElement> elementsToRemove = new HashSet<>();

  private final Map<DomElement, Map<String, Set<String>>> attributesToRemove = new HashMap<>();

  private final Map<DomElement, Convertible> convertibles = new HashMap<>();
  private final Map<String, List<ElementCheckResult>> referencesToCreate = new HashMap<>();

  public Map<DomElement, Convertible> getConvertibles() {
    return convertibles;
  }

  public Set<DomElement> getElementsToRemove() {
    return elementsToRemove;
  }

  public Map<DomElement, Map<String, Set<String>>> getAttributesToRemove() {
    return attributesToRemove;
  }

  public void addAttributeToRemove(DomElement element, String namespaceUri, String localName) {
    attributesToRemove
        .computeIfAbsent(element, e -> new HashMap<>())
        .computeIfAbsent(namespaceUri, n -> new HashSet<>())
        .add(localName);
  }

  public void addConvertible(DomElement element, Convertible convertible) {
    if (convertibles.containsKey(element)) {
      throw new IllegalStateException("There must only be one convertible per element!");
    }
    convertibles.put(element, convertible);
  }

  public Map<String, List<ElementCheckResult>> getReferencesToCreate() {
    return referencesToCreate;
  }
}
