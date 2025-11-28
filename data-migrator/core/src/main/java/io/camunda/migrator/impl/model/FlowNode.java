/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.model;

import java.util.Objects;

/**
 * Represents a flow node in the migration process with its activity ID and optional subprocess instance ID.
 */
public class FlowNode {

  protected String activityId;
  protected String subProcessInstanceId;

  public FlowNode(String activityId, String subProcessInstanceId) {
    this.activityId = activityId;
    this.subProcessInstanceId = subProcessInstanceId;
  }

  public String activityId() {
    return activityId;
  }

  public String subProcessInstanceId() {
    return subProcessInstanceId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (FlowNode) obj;
    return Objects.equals(this.activityId, that.activityId) && Objects.equals(this.subProcessInstanceId, that.subProcessInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(activityId, subProcessInstanceId);
  }

  @Override
  public String toString() {
    return "FlowNode[" + "activityId=" + activityId + ", " + "subProcessInstanceId=" + subProcessInstanceId + ']';
  }

}
