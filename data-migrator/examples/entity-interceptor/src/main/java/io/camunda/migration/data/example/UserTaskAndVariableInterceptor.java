/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.example;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example entity interceptor that handles multiple specific entity types.
 *
 * This demonstrates:
 * - How to handle multiple entity types in a single interceptor
 * - How to safely identify and process different entity types
 * - How to apply type-specific transformations
 * - How to add custom validation logic
 */
public class UserTaskAndVariableInterceptor implements EntityInterceptor {

  protected static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskAndVariableInterceptor.class);

  // Configurable properties that can be set via application.yml
  protected boolean normalizeAssignees = true;
  protected boolean validateVariables = true;
  protected String assigneePrefix = "";

  /**
   * This interceptor handles both user tasks and variables.
   */
  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricTaskInstance.class, HistoricVariableInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    // Determine which entity type we're processing and handle accordingly
    if (context.getC7Entity() instanceof HistoricTaskInstance) {
      handleUserTask(context);
    } else if (context.getC7Entity() instanceof HistoricVariableInstance) {
      handleVariable(context);
    }
  }

  /**
   * Handles user task entity conversion.
   */
  protected void handleUserTask(EntityConversionContext<?, ?> context) {
    HistoricTaskInstance c7Task = (HistoricTaskInstance) context.getC7Entity();
    UserTaskDbModel.Builder c8Builder = (UserTaskDbModel.Builder) context.getC8DbModelBuilder();

    LOGGER.debug("Processing user task: {} (name: {})", c7Task.getId(), c7Task.getName());

    if (normalizeAssignees && c7Task.getAssignee() != null) {
      String originalAssignee = c7Task.getAssignee();
      String normalizedAssignee = normalizeAssignee(originalAssignee);

      // Note: The actual builder method depends on the UserTaskDbModel implementation
      // This is an example of the pattern
      try {
        c8Builder.getClass()
            .getMethod("assignee", String.class)
            .invoke(c8Builder, normalizedAssignee);

        LOGGER.debug(
            "Normalized assignee from '{}' to '{}' for task {}",
            originalAssignee,
            normalizedAssignee,
            c7Task.getId());
      } catch (Exception e) {
        LOGGER.trace("Could not set assignee on builder", e);
      }
    }

    // Example: Add custom logic for specific task types
    if (c7Task.getTaskDefinitionKey() != null
        && c7Task.getTaskDefinitionKey().startsWith("approval")) {
      LOGGER.info("Processing approval task: {}", c7Task.getId());
      // Add custom approval task handling here
    }
  }

  /**
   * Handles variable entity conversion.
   */
  protected void handleVariable(EntityConversionContext<?, ?> context) {
    HistoricVariableInstance c7Variable = (HistoricVariableInstance) context.getC7Entity();
    VariableDbModel.VariableDbModelBuilder c8Builder = (VariableDbModel.VariableDbModelBuilder) context.getC8DbModelBuilder();

    LOGGER.debug(
        "Processing variable: {} (type: {})",
        c7Variable.getName(),
        c7Variable.getVariableTypeName());

    if (validateVariables) {
      validateVariable(c7Variable);
    }

    // Example: Handle sensitive variables
    if (isSensitiveVariable(c7Variable.getName())) {
      LOGGER.info("Processing sensitive variable: {}", c7Variable.getName());
      // Add custom handling for sensitive data
      // For example, you might want to log access or apply additional transformations
    }
  }

  /**
   * Normalizes an assignee name by applying the configured prefix and trimming whitespace.
   */
  protected String normalizeAssignee(String assignee) {
    String normalized = assignee.trim();
    if (!assigneePrefix.isEmpty()) {
      normalized = assigneePrefix + normalized;
    }
    return normalized;
  }

  /**
   * Validates a variable and logs warnings for potential issues.
   */
  protected void validateVariable(HistoricVariableInstance variable) {
    if (variable.getName() == null || variable.getName().isEmpty()) {
      LOGGER.warn("Variable with empty name found in process instance: {}",
          variable.getProcessInstanceId());
    }

    if (variable.getValue() == null) {
      LOGGER.debug("Variable {} has null value", variable.getName());
    }
  }

  /**
   * Checks if a variable name indicates sensitive data.
   */
  protected boolean isSensitiveVariable(String variableName) {
    if (variableName == null) {
      return false;
    }
    String lowerName = variableName.toLowerCase();
    return lowerName.contains("password")
        || lowerName.contains("secret")
        || lowerName.contains("token")
        || lowerName.contains("api_key");
  }

  // Setter methods for config properties
  public void setNormalizeAssignees(boolean normalizeAssignees) {
    this.normalizeAssignees = normalizeAssignees;
  }

  public void setValidateVariables(boolean validateVariables) {
    this.validateVariables = validateVariables;
  }

  public void setAssigneePrefix(String assigneePrefix) {
    this.assigneePrefix = assigneePrefix;
  }

  // Getter methods
  public boolean isNormalizeAssignees() {
    return normalizeAssignees;
  }

  public boolean isValidateVariables() {
    return validateVariables;
  }

  public String getAssigneePrefix() {
    return assigneePrefix;
  }
}

