/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

/**
 * Centralized logging utility for C7Client.
 * Contains all log messages and string constants used in C7Client.
 */
public class C7ClientLogs {

  // C7 Client Error Messages
  public static final String FAILED_TO_FETCH_ACTIVITY_INSTANCE = "Failed to fetch activity instance for processInstanceId: ";
  public static final String FAILED_TO_FETCH_DEPLOYMENT_TIME = "Failed to fetch deployment time for definition with C7 ID: ";
  public static final String FAILED_TO_FETCH_BPMN_XML = "Failed to fetch BPMN model instance for process definition Id: ";
  public static final String FAILED_TO_FETCH_PROCESS_INSTANCE = "Process instance fetching failed for C7 ID: ";
  public static final String FAILED_TO_FETCH_HISTORIC_ELEMENT = "Failed to fetch %s for C7 ID: %s";
  public static final String FAILED_TO_FETCH_TENANTS = "Failed to fetch tenants";
}
