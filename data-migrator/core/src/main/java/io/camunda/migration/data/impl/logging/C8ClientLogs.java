/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.impl.logging;

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
  public static final String FAILED_TO_MIGRATE_TENANT = "Failed to migrate tenant with ID: ";
  public static final String FAILED_TO_INSERT_PROCESS_INSTANCE = "Failed to insert process instance";
  public static final String FAILED_TO_FIND_PROCESS_INSTANCE_BY_KEY = "Failed to find process instance by key: ";
  public static final String FAILED_TO_SEARCH_PROCESS_INSTANCE = "Failed to search process instances";
  public static final String FAILED_TO_INSERT_PROCESS_DEFINITION = "Failed to insert process definition";
  public static final String FAILED_TO_INSERT_DECISION_REQUIREMENTS = "Failed to insert decision requirements";
  public static final String FAILED_TO_INSERT_DECISION_DEFINITION = "Failed to insert decision definition";
  public static final String FAILED_TO_SEARCH_DECISION_DEFINITIONS = "Failed to search decision definitions";
  public static final String FAILED_TO_INSERT_DECISION_INSTANCE = "Failed to insert decision instance";
  public static final String FAILED_TO_INSERT_DECISION_INSTANCE_INPUT = "Failed to insert decision instance input";
  public static final String FAILED_TO_INSERT_DECISION_INSTANCE_OUTPUT = "Failed to insert decision instance output";
  public static final String FAILED_TO_SEARCH_DECISION_INSTANCES = "Failed to search decision instances";
  public static final String FAILED_TO_INSERT_INCIDENT = "Failed to insert incident";
  public static final String FAILED_TO_INSERT_VARIABLE = "Failed to insert variable";
  public static final String FAILED_TO_INSERT_USER_TASK = "Failed to insert user task";
  public static final String FAILED_TO_INSERT_FLOW_NODE_INSTANCE = "Failed to insert flow node instance";
  public static final String FAILED_TO_SEARCH_FLOW_NODE_INSTANCES = "Failed to search flow node instances";
  public static final String FAILED_TO_SEARCH_USER_TASKS = "Failed to search user tasks";
  public static final String FAILED_TO_INSERT_AUDIT_LOG = "Failed to insert audit log";
  public static final String FAILED_TO_INSERT_JOB = "Failed to insert job";
  public static final String FAILED_TO_MIGRATE_AUTHORIZATION = "Failed to migrate authorization with legacy ID: ";
}
