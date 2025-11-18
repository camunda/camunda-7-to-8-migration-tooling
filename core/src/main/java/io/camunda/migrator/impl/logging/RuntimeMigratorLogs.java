/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.RuntimeMigrator;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs debug messages for RuntimeMigrator operations
 */
public class RuntimeMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMigrator.class);

  // RuntimeMigrator Messages
  public static final String STARTING_NEW_C8_PROCESS_INSTANCE = "Starting new C8 process instance with C7 ID: [{}]";
  public static final String STARTED_C8_PROCESS_INSTANCE = "Started C8 process instance with processInstanceKey: [{}]";
  public static final String SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR = "Skipping process instance with C7 ID: {}; due to: {} Enable DEBUG level to print the stacktrace.";
  public static final String STACKTRACE = "Stacktrace:";
  public static final String SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR = "Skipping process instance with C7 ID [{}]: {}";
  public static final String FETCHING_PROCESS_INSTANCES = "Fetching process instances to migrate";
  public static final String FETCHING_LATEST_CREATE_TIME = "Fetching latest create time of process instances";
  public static final String LATEST_CREATE_TIME = "Latest create time: {}";
  public static final String PROCESS_INSTANCE_NOT_EXISTS = "Process instance with C7 ID {} doesn't exist anymore. Has it been completed or cancelled in the meantime?";
  public static final String ACTIVATING_MIGRATOR_JOBS = "Activating migrator jobs";
  public static final String MIGRATOR_JOBS_FOUND = "Migrator jobs found: {}";
  public static final String COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES = "Collecting active descendant activity instances for activityId [{}]";
  public static final String FOUND_ACTIVE_ACTIVITIES_TO_ACTIVATE = "Found {} active activity instances to activate";
  public static final String EXTERNALLY_STARTED_PROCESS_INSTANCE = "Process instance with key [{}] was externally started, skipping migrator job activation.";

  public static void startingNewC8ProcessInstance(String c7ProcessInstanceId) {
    LOGGER.debug(STARTING_NEW_C8_PROCESS_INSTANCE, c7ProcessInstanceId);
  }

  public static void startedC8ProcessInstance(Long processInstanceKey) {
    LOGGER.debug(STARTED_C8_PROCESS_INSTANCE, processInstanceKey);
  }

  public static void skippingProcessInstanceVariableError(String c7ProcessInstanceId, String message) {
    LOGGER.info(SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR, c7ProcessInstanceId, message);
  }

  public static void stacktrace(Exception e) {
    LOGGER.debug(STACKTRACE, e);
  }

  public static void skippingProcessInstanceValidationError(String c7ProcessInstanceId, String message) {
    LOGGER.warn(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, c7ProcessInstanceId, message);
  }

  public static void fetchingProcessInstances() {
    LOGGER.info(FETCHING_PROCESS_INSTANCES);
  }

  public static void fetchingLatestCreateTime() {
    LOGGER.debug(FETCHING_LATEST_CREATE_TIME);
  }

  public static void latestCreateTime(Date createTime) {
    LOGGER.debug(LATEST_CREATE_TIME, createTime);
  }

  public static void processInstanceNotExists(String c7ProcessInstanceId) {
    LOGGER.warn(PROCESS_INSTANCE_NOT_EXISTS, c7ProcessInstanceId);
  }

  public static void activatingMigratorJobs() {
    LOGGER.info(ACTIVATING_MIGRATOR_JOBS);
  }

  public static void migratorJobsFound(int size) {
    LOGGER.debug(MIGRATOR_JOBS_FOUND, size);
  }

  public static void collectingActiveDescendantActivities(String activityId) {
    LOGGER.debug(COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES, activityId);
  }

  public static void foundActiveActivitiesToActivate(int size) {
    LOGGER.debug(FOUND_ACTIVE_ACTIVITIES_TO_ACTIVATE, size);
  }

  public static void externallyStartedProcessInstance(long processInstanceKey) {
    LOGGER.info(EXTERNALLY_STARTED_PROCESS_INSTANCE, processInstanceKey);
  }

  public static void rollingBackProcessInstances(int count) {
    LOGGER.warn("Rolling back {} process instances due to batch insert failure", count);
  }

  public static void rolledBackProcessInstance(Long processInstanceKey) {
    LOGGER.info("Successfully rolled back process instance with key: {}", processInstanceKey);
  }

  public static void failedToRollbackProcessInstance(Long processInstanceKey, Exception e) {
    LOGGER.error("Failed to rollback process instance with key: {}", processInstanceKey, e);
  }
}
