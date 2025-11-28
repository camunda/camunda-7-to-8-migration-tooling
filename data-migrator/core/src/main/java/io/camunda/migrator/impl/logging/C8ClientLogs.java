/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

/**
 * Centralized logging utility for C8Client.
 * Contains all log messages and string constants used in C8Client.
 */
public class C8ClientLogs {

  // C8 Client Error Messages
  public static final String FAILED_TO_DEPLOY_C8_RESOURCES = "Failed to deploy C8 resources: ";
  public static final String FAILED_TO_CREATE_PROCESS_INSTANCE = "Creating process instance failed for bpmnProcessId: ";
  public static final String FAILED_TO_ACTIVATE_JOBS = "Failed to activate jobs for type: ";
  public static final String FAILED_TO_FETCH_PROCESS_DEFINITION_XML = "Failed to fetch process definition XML for key: ";
  public static final String FAILED_TO_FETCH_VARIABLE = "Failed to fetch variable '%s' from job: %s";
  public static final String FAILED_TO_MODIFY_PROCESS_INSTANCE = "Failed to modify process instance with activation for key: ";
  public static final String FAILED_TO_SEARCH_PROCESS_DEFINITIONS = "Process definition search failed for processDefinitionId: ";
}
