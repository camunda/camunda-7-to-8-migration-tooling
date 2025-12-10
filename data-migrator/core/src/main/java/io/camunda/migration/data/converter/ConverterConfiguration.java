/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.converter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConverterConfiguration {

  @Bean
  public DecisionDefinitionConverter decisionDefinitionConverter() {
    return new DecisionDefinitionConverter();
  }

  @Bean
  public DecisionRequirementsDefinitionConverter decisionRequirementsDefinitionConverter() {
    return new DecisionRequirementsDefinitionConverter();
  }

  @Bean
  public DecisionInstanceConverter decisionInstanceConverter() {
    return new DecisionInstanceConverter();
  }

  @Bean
  public FlowNodeConverter flowNodeConverter() {
    return new FlowNodeConverter();
  }

  @Bean
  public IncidentConverter incidentConverter() {
    return new IncidentConverter();
  }

  @Bean
  public ProcessDefinitionConverter processDefinitionConverter() {
    return new ProcessDefinitionConverter();
  }

  @Bean
  public ProcessInstanceConverter processInstanceConverter() {
    return new ProcessInstanceConverter();
  }

  @Bean
  public UserTaskConverter userTaskConverter() {
    return new UserTaskConverter();
  }

  @Bean
  public VariableConverter variableConverter() {
    return new VariableConverter();
  }

}
