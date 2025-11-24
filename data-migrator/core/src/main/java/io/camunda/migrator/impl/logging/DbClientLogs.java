/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs debug messages for DbClient operations
 */
public class DbClientLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(DbClient.class);

  // DbClient Messages
  public static final String UPDATING_KEY_FOR_C7_ID = "Updating key for C7 ID [{}] with value [{}]";
  public static final String UPDATING_SKIP_REASON = "Updating skip reason for C7 ID [{}] with value [{}]";
  public static final String INSERTING_RECORD = "Inserting record [{}], [{}], [{}], [{}]";
  public static final String FOUND_CREATE_TIME_FOR_TYPE = "Latest create time for {}: {}";

  // DbClient Error Messages
  public static final String FAILED_TO_CHECK_EXISTENCE = "Failed to check existence for C7 ID: ";
  public static final String FAILED_TO_CHECK_KEY = "Failed to check key for C7 ID: ";
  public static final String FAILED_TO_FIND_ALL = "Failed to find all C7 IDs";
  public static final String FAILED_TO_FIND_LATEST_CREATE_TIME = "Failed to find latest create time for type: ";
  public static final String FAILED_TO_FIND_KEY_BY_ID = "Failed to find key by C7 ID: ";
  public static final String FAILED_TO_UPDATE_KEY = "Failed to update key for C7 ID: ";
  public static final String FAILED_TO_UPDATE_SKIP_REASON = "Failed to update skip reason for C7 ID: ";
  public static final String FAILED_TO_INSERT_RECORD = "Failed to insert record for C7 ID: ";
  public static final String FAILED_TO_FIND_SKIPPED_COUNT = "Failed to find skipped count";
  public static final String FAILED_TO_FIND_ALL_SKIPPED = "Failed to find skipped C7 IDs";
  public static final String FAILED_TO_DELETE = "Failed to delete mapping for C7 ID: ";
  public static final String FAILED_TO_DROP_MIGRATION_TABLE = "Failed to drop migration mapping table";

  public static void updatingC8KeyForC7Id(String c7Id, Long c8Key) {
    LOGGER.debug(UPDATING_KEY_FOR_C7_ID, c7Id, c8Key);
  }

  public static void updatingSkipReason(String c7Id, String skipReason) {
    LOGGER.debug(UPDATING_SKIP_REASON, c7Id, skipReason);
  }

  public static void insertingRecord(String c7Id, Object startDate, Long c8Key, String skipReason) {
    LOGGER.debug(INSERTING_RECORD, c7Id, startDate, c8Key, skipReason);
  }

  public static void foundLatestCreateTime(Date latestCreateTime, TYPE type) {
    LOGGER.debug(FOUND_CREATE_TIME_FOR_TYPE, type, latestCreateTime);
  }
}
