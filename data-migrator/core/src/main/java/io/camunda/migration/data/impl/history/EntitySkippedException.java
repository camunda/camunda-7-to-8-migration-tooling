/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import java.util.Date;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.repository.DecisionDefinition;

public class EntitySkippedException extends RuntimeException {

  protected C7Entity<?> c7Entity;
  protected String message;

  public EntitySkippedException(C7Entity<?> c7Entity, String message) {
    this.c7Entity = c7Entity;
    this.message = message;
  }

  public EntitySkippedException(UserOperationLogEntry userOperationLogEntry, String message) {
    this(C7Entity.of(userOperationLogEntry), message);
  }

  public EntitySkippedException(HistoricDecisionInstance c7DecisionInstance, String message) {
    this(C7Entity.of(c7DecisionInstance), message);
  }

  public EntitySkippedException(DecisionDefinition c7DecisionDefinition, Date creationTime, String message) {
    this(C7Entity.of(c7DecisionDefinition, creationTime), message);
  }

  public EntitySkippedException(HistoricActivityInstance c7FlowNode, String message) {
    this(C7Entity.of(c7FlowNode), message);
  }

  public EntitySkippedException(HistoricExternalTaskLog c7ExternalTaskLog, String message) {
    this(C7Entity.of(c7ExternalTaskLog), message);
  }

  public EntitySkippedException(HistoricIncident c7Incident, String message) {
    this(C7Entity.of(c7Incident), message);
  }

  public EntitySkippedException(HistoricProcessInstance c7ProcessInstance, String message) {
    this(C7Entity.of(c7ProcessInstance), message);
  }

  public EntitySkippedException(HistoricTaskInstance c7UserTask, String message) {
    this(C7Entity.of(c7UserTask), message);
  }

  public EntitySkippedException(HistoricVariableInstance c7Variable, String message) {
    this(C7Entity.of(c7Variable), message);
  }

  @Override
  public String getMessage() {
    return message;
  }

  public C7Entity<?> getC7Entity() {
    return c7Entity;
  }

}
