/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.model;

/**
 * Represents a flow node in the migration process with its activity ID and optional subprocess instance ID.
 */
public record FlowNode(String activityId, String subProcessInstanceId) {
}
