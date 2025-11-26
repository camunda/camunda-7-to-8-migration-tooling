/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.model;

import java.util.Map;

/**
 * Represents the activation data for a specific activity during process instance migration.
 * Contains the variables that should be applied when activating the activity.
 */
public record FlowNodeActivation(String activityId, Map<String, Object> variables) {
}
