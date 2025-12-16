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
  public DecisionDefinitionTransformer decisionDefinitionTransformer() {
    return new DecisionDefinitionTransformer();
  }

  @Bean
  public DecisionRequirementsDefinitionTransformer decisionRequirementsDefinitionTransformer() {
    return new DecisionRequirementsDefinitionTransformer();
  }

  @Bean
  public DecisionInstanceTransformer decisionInstanceTransformer() {
    return new DecisionInstanceTransformer();
  }

  @Bean
  public FlowNodeTransformer flowNodeTransformer() {
    return new FlowNodeTransformer();
  }

  @Bean
  public IncidentTransformer incidentTransformer() {
    return new IncidentTransformer();
  }

  @Bean
  public ProcessDefinitionTransformer processDefinitionTransformer() {
    return new ProcessDefinitionTransformer();
  }

  @Bean
  public ProcessInstanceTransformer processInstanceTransformer() {
    return new ProcessInstanceTransformer();
  }

  @Bean
  public UserTaskTransformer userTaskTransformer() {
    return new UserTaskTransformer();
  }

  @Bean
  public VariableTransformer variableTransformer() {
    return new VariableTransformer();
  }

}
