/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import org.camunda.bpm.engine.impl.history.event.HistoricDecisionInputInstanceEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricDecisionOutputInstanceEntity;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.TypedValue;

/**
 * Represents a context of a variable within a process instance.
 */
public class VariableContext {

  protected String name;
  protected TypedValue c7Value;
  protected Object c8Value;

  protected ValueFields valueFields;

  public VariableContext(ValueFields variable) {
    this.name = variable.getName();
    this.valueFields = variable;

    switch (variable) {
    case HistoricDecisionOutputInstanceEntity var -> c7Value = var.getTypedValue(false);
    case HistoricDecisionInputInstanceEntity var -> c7Value = var.getTypedValue(false);
    case VariableInstanceEntity var -> c7Value = var.getTypedValue(false);
    case HistoricVariableInstanceEntity var -> c7Value = var.getTypedValue(false);
    case null, default -> throw new IllegalArgumentException("Variable cannot be null");
    }
  }

  /**
   * Gets the value to be used in Camunda 8 for this variable.
   *
   * @return the C8 compatible value
   */
  public Object getC8Value() {
    return c8Value;
  }

  /**
   * Sets the value to be used in Camunda 8 for this variable.
   *
   * @param value the C8 compatible value
   */
  public void setC8Value(Object value) {
    this.c8Value = value;
  }

  /**
   * Gets the name of the variable.
   *
   * @return the variable name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the Camunda 7 typed value of the variable.
   *
   * @return the C7 typed value
   */
  public TypedValue getC7TypedValue() {
    return c7Value;
  }

  /**
   * Gets the raw value of the Camunda 7 variable.
   *
   * @return the C7 raw value
   */
  public Object getC7Value() {
    return getC7TypedValue().getValue();
  }

  /**
   * Gets the underlying entity representing the variable.
   * You can cast this to specific entity types like
   * {@link VariableInstanceEntity}, {@link HistoricVariableInstanceEntity},
   * {@link HistoricDecisionInputInstanceEntity}, or {@link HistoricDecisionOutputInstanceEntity}.
   *
   * @return the ValueFields entity of the variable
   */
  public ValueFields getEntity() {
    return valueFields;
  }

  /**
   * Checks if this variable is from a history context.
   * <p>
   * Returns true for history variable instances.
   * </p>
   *
   * @return true if this is a history variable, false otherwise
   */
  public boolean isHistory() {
    return HistoricDecisionInputInstanceEntity.class.isAssignableFrom(getEntity().getClass()) ||
        HistoricDecisionOutputInstanceEntity.class.isAssignableFrom(getEntity().getClass()) ||
        HistoricVariableInstanceEntity.class.isAssignableFrom(getEntity().getClass());
  }

  /**
   * Checks if this variable is from a runtime context.
   * <p>
   * Returns true for runtime variable instances.
   * </p>
   *
   * @return true if this is a runtime variable, false otherwise
   */
  public boolean isRuntime() {
    return VariableInstanceEntity.class.isAssignableFrom(valueFields.getClass());
  }

}
