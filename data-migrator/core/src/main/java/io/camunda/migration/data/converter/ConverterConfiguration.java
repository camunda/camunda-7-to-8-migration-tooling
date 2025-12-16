/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.converter;

import io.camunda.migration.data.impl.interceptor.history.entity.DecisionDefinitionTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.DecisionInstanceTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.DecisionRequirementsDefinitionTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.FlowNodeTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.IncidentTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.ProcessDefinitionTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.UserTaskTransformer;
import io.camunda.migration.data.impl.interceptor.history.entity.VariableTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConverterConfiguration {

  @Bean
  public DecisionDefinitionTransformer decisionDefinitionConverter() {
    return new DecisionDefinitionTransformer();
  }

  @Bean
  public DecisionRequirementsDefinitionTransformer decisionRequirementsDefinitionConverter() {
    return new DecisionRequirementsDefinitionTransformer();
  }

  @Bean
  public DecisionInstanceTransformer decisionInstanceConverter() {
    return new DecisionInstanceTransformer();
  }

  @Bean
  public FlowNodeTransformer flowNodeConverter() {
    return new FlowNodeTransformer();
  }

  @Bean
  public IncidentTransformer incidentConverter() {
    return new IncidentTransformer();
  }

  @Bean
  public ProcessDefinitionTransformer processDefinitionConverter() {
    return new ProcessDefinitionTransformer();
  }

  @Bean
  public ProcessInstanceTransformer processInstanceConverter() {
    return new ProcessInstanceTransformer();
  }

  @Bean
  public UserTaskTransformer userTaskConverter() {
    return new UserTaskTransformer();
  }

  @Bean
  public VariableTransformer variableConverter() {
    return new VariableTransformer();
  }

}
