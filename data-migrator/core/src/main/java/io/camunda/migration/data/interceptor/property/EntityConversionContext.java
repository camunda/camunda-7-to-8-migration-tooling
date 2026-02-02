/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor.property;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import org.camunda.bpm.engine.ProcessEngine;

/**
 * Context for entity conversion from Camunda 7 to Camunda 8.
 * Stores the C7 entity, entity type, C8 database model builder, and process engine.
 *
 * @param <C7> the Camunda 7 entity type
 * @param <C8> the Camunda 8 database model builder type
 */
public class EntityConversionContext<C7, C8> {

  protected final C7 c7Entity;
  protected C8 c8DbModelBuilder;
  protected ProcessEngine processEngine;

  /**
   * Creates a new context with a C7 entity.
   *
   * @param c7Entity the Camunda 7 entity
   * @throws IllegalArgumentException if c7Entity is null
   */
  public EntityConversionContext(C7 c7Entity) {
    this(c7Entity, null);
  }

  /**
   * Creates a new context with a C7 entity and process engine.
   *
   * @param c7Entity the Camunda 7 entity
   * @param processEngine the Camunda 7 process engine
   * @throws IllegalArgumentException if c7Entity is null
   */
  public EntityConversionContext(C7 c7Entity, ProcessEngine processEngine) {
    this(c7Entity, null, processEngine);
  }

  /**
   * Creates a new context with a C7 entity, C8 database model builder, and process engine.
   *
   * @param c7Entity the Camunda 7 entity
   * @param c8DbModelBuilder the Camunda 8 database model builder
   * @param processEngine the Camunda 7 process engine
   * @throws IllegalArgumentException if c7Entity is null (except for DecisionRequirementsDbModel.Builder edge case)
   */
  public EntityConversionContext(C7 c7Entity, C8 c8DbModelBuilder, ProcessEngine processEngine) {
    if (c7Entity == null && !(c8DbModelBuilder instanceof DecisionRequirementsDbModel.Builder)) {
      throw new IllegalArgumentException("C7 entity cannot be null other than for DecisionRequirementsDbModel.Builder edge case");
    }
    this.c7Entity = c7Entity;
    this.c8DbModelBuilder = c8DbModelBuilder;
    this.processEngine = processEngine;
  }

  /**
   * Gets the Camunda 7 entity.
   *
   * @return the C7 entity
   */
  public C7 getC7Entity() {
    return c7Entity;
  }

  /**
   * Gets the Camunda 8 database model builder.
   *
   * @return the C8 database model builder
   */
  public C8 getC8DbModelBuilder() {
    return c8DbModelBuilder;
  }

  /**
   * Sets the Camunda 8 database model builder.
   *
   * @param c8DbModelBuilder the C8 database model builder to set
   */
  public void setC8DbModelBuilder(C8 c8DbModelBuilder) {
    this.c8DbModelBuilder = c8DbModelBuilder;
  }

  /**
   * Gets the Camunda Process Engine.
   *
   * @return the process engine
   */
  public ProcessEngine getProcessEngine() {
    return processEngine;
  }
}

