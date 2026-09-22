/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/** Marks invalid or deprecated Camunda client settings in Spring Boot YAML files. */
public class ValidateCamundaClientYaml extends Recipe {

  /** Instantiates a Camunda client YAML validator. */
  public ValidateCamundaClientYaml() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Validate Camunda client YAML settings";
  }

  @Override
  public @NonNull String getDescription() {
    return "Validates Camunda client configuration values and shapes against the target starter,"
        + " and marks legacy Zeebe property aliases as deprecated.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new YamlIsoVisitor<>() {
      @Override
      public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
        if (isSequenceElement(getCursor())) {
          return mapping;
        }
        Yaml.Mapping m = super.visitMapping(mapping, ctx);
        String parentPath = ancestorPath(getCursor());
        return m.withEntries(
            ListUtils.map(
                m.getEntries(),
                entry -> {
                  String key = join(parentPath, entry.getKey().getValue());
                  if (!(entry.getValue() instanceof Yaml.Scalar value)) {
                    if (entry.getValue() instanceof Yaml.Sequence sequence) {
                      List<String> scalarValues =
                          sequence.getEntries().stream()
                              .map(Yaml.Sequence.Entry::getBlock)
                              .filter(Yaml.Scalar.class::isInstance)
                              .map(Yaml.Scalar.class::cast)
                              .map(Yaml.Scalar::getValue)
                              .toList();
                      Optional<String> sequenceFinding =
                          CamundaClientConfigurationValidation.sequenceFinding(key, scalarValues);
                      if (sequenceFinding.isPresent()) {
                        return SearchResult.found(entry, sequenceFinding.get());
                      }
                      for (Yaml.Sequence.Entry sequenceEntry : sequence.getEntries()) {
                        Yaml.Block element = sequenceEntry.getBlock();
                        if (element instanceof Yaml.Scalar) {
                          continue;
                        }
                        CamundaClientConfigurationValidation.ValueShape elementShape =
                            element instanceof Yaml.Sequence
                                ? CamundaClientConfigurationValidation.ValueShape.SEQUENCE
                                : CamundaClientConfigurationValidation.ValueShape.MAPPING;
                        Optional<String> elementFinding =
                            CamundaClientConfigurationValidation.sequenceElementFinding(
                                key, elementShape);
                        if (elementFinding.isPresent()) {
                          return SearchResult.found(entry, elementFinding.get());
                        }
                      }
                      return entry;
                    }
                    if (entry.getValue() instanceof Yaml.Mapping nestedMapping) {
                      if (CamundaClientConfigurationValidation.isAuthenticationContainer(key)
                          || (!nestedMapping.getEntries().isEmpty()
                              && CamundaClientConfigurationValidation
                                  .isUnknownAuthenticationDescendant(key))) {
                        return entry;
                      }
                    }
                    return CamundaClientConfigurationValidation.shapeFinding(
                            key, CamundaClientConfigurationValidation.ValueShape.MAPPING)
                        .map(message -> SearchResult.found(entry, message))
                        .orElse(entry);
                  }
                  return CamundaClientConfigurationValidation.finding(key, value.getValue())
                      .map(message -> SearchResult.found(entry, message))
                      .orElse(entry);
                }));
      }
    };
  }

  private static String ancestorPath(Cursor mappingCursor) {
    Deque<String> parts = new ArrayDeque<>();
    Cursor cursor = mappingCursor.getParent();
    while (cursor != null) {
      if (cursor.getValue() instanceof Yaml.Mapping.Entry entry) {
        parts.addFirst(entry.getKey().getValue());
      }
      cursor = cursor.getParent();
    }
    return String.join(".", parts);
  }

  private static boolean isSequenceElement(Cursor cursor) {
    Cursor parent = cursor.getParent();
    while (parent != null) {
      if (parent.getValue() instanceof Yaml.Sequence.Entry) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  private static String join(String parentPath, String key) {
    return parentPath.isEmpty() ? key : parentPath + "." + key;
  }
}
