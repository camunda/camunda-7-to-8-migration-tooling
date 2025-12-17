/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.example;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example type-specific entity interceptor that enriches process instance data
 * by accessing the Camunda 7 process engine.
 *
 * This demonstrates:
 * - How to restrict an interceptor to specific entity types
 * - How to safely cast entities and builders
 * - How to access the Camunda 7 process engine
 * - How to enrich entity data with additional information
 */
public class ProcessInstanceEnricher implements EntityInterceptor {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceEnricher.class);

  // Configurable properties
  protected boolean enrichMetadata = true;
  protected boolean enableLogging = true;

  /**
   * Restrict this interceptor to only handle process instances.
   * This improves performance by avoiding unnecessary calls.
   */
  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    // Safe to cast because getTypes() restricts to HistoricProcessInstance
    HistoricProcessInstance c7Instance = (HistoricProcessInstance) context.getC7Entity();
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder c8Builder =
        (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (enableLogging) {
      LOGGER.info(
          "Enriching process instance: {} (definition: {})",
          c7Instance.getId(),
          c7Instance.getProcessDefinitionKey());
    }

    if (enrichMetadata) {
      enrichWithDeploymentInfo(c7Instance, c8Builder, context.getProcessEngine());
    }

    if (enableLogging) {
      LOGGER.info(
          "Completed enrichment for process instance: {}",
          c7Instance.getId());
    }
  }

  /**
   * Enriches the process instance with deployment information from Camunda 7.
   * This demonstrates accessing the process engine to retrieve additional data.
   */
  protected void enrichWithDeploymentInfo(
      HistoricProcessInstance c7Instance,
      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder c8Builder,
      ProcessEngine processEngine) {

    try {
      // Query the Camunda 7 process engine for process definition details
      ProcessDefinition processDefinition = processEngine.getRepositoryService()
          .createProcessDefinitionQuery()
          .processDefinitionId(c7Instance.getProcessDefinitionId())
          .singleResult();

      if (processDefinition != null) {
        String deploymentId = processDefinition.getDeploymentId();

        if (enableLogging) {
          LOGGER.debug(
              "Found deployment ID: {} for process instance: {}",
              deploymentId,
              c7Instance.getId());
        }

        // Example: You could store deployment ID in custom properties
        // or use it to make additional transformations
        // Note: The actual ProcessInstanceDbModel may not have all these methods,
        // this is just an example of the pattern

        if (enableLogging) {
          LOGGER.debug(
              "Process definition version: {}, deployment time: {}",
              processDefinition.getVersion(),
              processDefinition.getDeploymentId());
        }
      } else {
        LOGGER.warn(
            "Process definition not found for ID: {}",
            c7Instance.getProcessDefinitionId());
      }
    } catch (Exception e) {
      LOGGER.error(
          "Failed to enrich process instance {} with deployment info",
          c7Instance.getId(),
          e);
      // Don't rethrow - allow the migration to continue
    }
  }

  /**
   * Example of presetting parent properties.
   * This method is called before execute() and is useful for setting hierarchical relationships.
   */
  @Override
  public void presetParentProperties(EntityConversionContext<?, ?> context) {
    HistoricProcessInstance c7Instance = (HistoricProcessInstance) context.getC7Entity();
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder c8Builder =
        (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

    // Example: Handle parent process instance relationships
    if (c7Instance.getSuperProcessInstanceId() != null) {
      if (enableLogging) {
        LOGGER.debug(
            "Process instance {} has parent: {}",
            c7Instance.getId(),
            c7Instance.getSuperProcessInstanceId());
      }
      // Parent key would be set here in the actual implementation
      // c8Builder.parentProcessInstanceKey(...);
    }
  }

  // Setter methods for config properties
  public void setEnrichMetadata(boolean enrichMetadata) {
    this.enrichMetadata = enrichMetadata;
  }

  public void setEnableLogging(boolean enableLogging) {
    this.enableLogging = enableLogging;
  }

  // Getter methods
  public boolean isEnrichMetadata() {
    return enrichMetadata;
  }

  public boolean isEnableLogging() {
    return enableLogging;
  }
}

