/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.*;

import java.util.Date;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinition;

public class C7Entity<C7> {

  protected String id;
  protected Date creationTime;
  protected C7 entity;
  protected TYPE type;

  public C7Entity(String id, Date creationTime, C7 entity) {
    this.id = id;
    this.creationTime = creationTime;
    this.entity = entity;
    this.type = TYPE.of(entity);
  }

  public static C7Entity<?> of(Object c7) {
    return switch (c7) {
      case HistoricDecisionInstance c7DecisionInstance -> of(c7DecisionInstance);
      case HistoricActivityInstance c7ActivityInstance -> of(c7ActivityInstance);
      case HistoricIncident c7Incident -> of(c7Incident);
      case HistoricProcessInstance c7ProcessInstance -> of(c7ProcessInstance);
      case HistoricTaskInstance c7TaskInstance -> of(c7TaskInstance);
      case HistoricVariableInstance c7VariableInstance -> of(c7VariableInstance);
      default -> throw new IllegalArgumentException("Unsupported C7 entity type: " + c7.getClass().getName());
    };
  }

  public static C7Entity<?> of(Object c7, Date creationTime) {
    return switch (c7) {
      case DecisionDefinition c7DecisionDefinition -> of(c7DecisionDefinition, creationTime);
      case DecisionRequirementsDefinition c7DecisionRequirements -> of(c7DecisionRequirements, creationTime);
      case ProcessDefinition c7ProcessDefinition -> of(c7ProcessDefinition, creationTime);
      default -> throw new IllegalArgumentException("Unsupported C7 entity type: " + c7.getClass().getName());
    };
  }

  public static C7Entity<DecisionDefinition> of(DecisionDefinition c7DecisionDefinition, Date creationTime) {
    return new C7Entity<>(c7DecisionDefinition.getId(), creationTime, c7DecisionDefinition);
  }

  public static C7Entity<DecisionRequirementsDefinition> of(DecisionRequirementsDefinition c7Entity, Date creationTime) {
    return new C7Entity<>(c7Entity.getId(), creationTime, c7Entity);
  }

  public static C7Entity<ProcessDefinition> of(ProcessDefinition c7Entity, Date creationTime) {
    return new C7Entity<>(c7Entity.getId(), creationTime, c7Entity);
  }

  public static C7Entity<HistoricDecisionInstance> of(HistoricDecisionInstance c7Entity) {
    return new C7Entity<>(c7Entity.getId(), c7Entity.getEvaluationTime(), c7Entity);
  }

  public static C7Entity<HistoricActivityInstance> of(HistoricActivityInstance c7Entity) {
    return new C7Entity<>(c7Entity.getId(), c7Entity.getStartTime(), c7Entity);
  }

  public static C7Entity<HistoricIncident> of(HistoricIncident c7Entity) {
    return new C7Entity<>(c7Entity.getId(), c7Entity.getCreateTime(), c7Entity);
  }

  public static C7Entity<HistoricProcessInstance> of(HistoricProcessInstance c7Entity) {
    return new C7Entity<>(c7Entity.getId(), c7Entity.getStartTime(), c7Entity);
  }

  public static C7Entity<HistoricTaskInstance> of(HistoricTaskInstance c7Entity) {
    return new C7Entity<>(c7Entity.getId(), c7Entity.getStartTime(), c7Entity);
  }

  public static C7Entity<HistoricVariableInstance> of(HistoricVariableInstance c7Entity) {
    return new C7Entity<>(c7Entity.getId(), c7Entity.getCreateTime(), c7Entity);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Date getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(Date creationTime) {
    this.creationTime = creationTime;
  }

  public C7 unwrap() {
    return entity;
  }

  public void wrap(C7 entity) {
    this.entity = entity;
  }

  public TYPE getType() {
    return type;
  }

  public void setType(TYPE type) {
    this.type = type;
  }
}
