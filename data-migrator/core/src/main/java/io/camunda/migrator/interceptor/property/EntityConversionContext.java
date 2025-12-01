/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor.property;

/**
 * Context for entity conversion from Camunda 7 to Camunda 8.
 * Stores the C7 entity, entity type, and C8 database model builder.
 *
 * @param <C7> the Camunda 7 entity type
 * @param <C8> the Camunda 8 database model builder type
 */
public class EntityConversionContext<C7, C8> {

  private final C7 c7Entity;
  private C8 c8DbModelBuilder;
  private final Class<?> entityType;

  /**
   * Creates a new context with a C7 entity and entity type.
   *
   * @param c7Entity the Camunda 7 entity
   * @param entityType the entity type class
   * @throws IllegalArgumentException if c7Entity is null
   */
  public EntityConversionContext(C7 c7Entity, Class<?> entityType) {
    if (c7Entity == null) {
      throw new IllegalArgumentException("C7 entity cannot be null");
    }
    this.c7Entity = c7Entity;
    this.entityType = entityType;
    this.c8DbModelBuilder = null;
  }

  /**
   * Creates a new context with a C7 entity, entity type, and C8 database model builder.
   *
   * @param c7Entity the Camunda 7 entity
   * @param entityType the entity type class
   * @param c8DbModelBuilder the Camunda 8 database model builder
   * @throws IllegalArgumentException if c7Entity is null
   */
  public EntityConversionContext(C7 c7Entity, Class<?> entityType, C8 c8DbModelBuilder) {
    if (c7Entity == null) {
      throw new IllegalArgumentException("C7 entity cannot be null");
    }
    this.c7Entity = c7Entity;
    this.entityType = entityType;
    this.c8DbModelBuilder = c8DbModelBuilder;
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
   * Gets the entity type class.
   *
   * @return the entity type
   */
  public Class<?> getEntityType() {
    return entityType;
  }
}

