/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;

/**
 * Represents an invocation of a variable within a process instance.
 */
public class VariableInvocation {

  protected VariableInstanceEntity c7Variable;
  protected MigrationVariableDto migrationVariable;

  public VariableInvocation(VariableInstanceEntity variable) {
    if (variable == null) {
      throw new IllegalArgumentException("Variable cannot be null");
    }
    this.c7Variable = variable;
    this.migrationVariable = new MigrationVariableDto(variable.getName(), variable.getTypedValue(false));
  }

  /**
   * Returns the variable instance entity associated with this invocation.
   *
   * @return the {@link VariableInstanceEntity}
   */
  public VariableInstanceEntity getC7Variable() {
    return c7Variable;
  }

  /**
   * Returns the DTO representation of the variable.
   *
   * @return the {@link MigrationVariableDto}
   */
  public MigrationVariableDto getMigrationVariable() {
    return migrationVariable;
  }

  /**
   * Sets the value of the variable in the migration context.
   *
   * @param value the new value to set
   */
  public void setVariableValue(Object value) {
    migrationVariable.setValue(value);
  }

}
