/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

/** Marks invalid or deprecated Camunda client properties in Spring Boot property files. */
public class ValidateCamundaClientProperties extends Recipe {

  /** Instantiates a Camunda client properties validator. */
  public ValidateCamundaClientProperties() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Validate Camunda client properties";
  }

  @Override
  public @NonNull String getDescription() {
    return "Marks unsupported Camunda client modes and authentication property shapes, and marks"
        + " legacy Zeebe property aliases as deprecated.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new PropertiesIsoVisitor<>() {
      @Override
      public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
        Properties.Entry e = super.visitEntry(entry, ctx);
        return CamundaClientConfigurationValidation.finding(e.getKey(), e.getValue().getText())
            .map(message -> SearchResult.found(e, message))
            .orElse(e);
      }
    };
  }
}
