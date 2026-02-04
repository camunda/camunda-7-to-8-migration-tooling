/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.logging;

import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentityMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(IdentityMigrator.class);

  // Event log constants
  public static final String MIGRATING_TENANT = "Migrating tenant [{}]";
  public static final String SUCCESSFULLY_MIGRATED_TENANT = "Successfully migrated tenant [{}]";
  public static final String SKIPPED_TENANT = "Tenant with ID [{}] was skipped";
  public static final String MIGRATING_AUTH = "Migrating authorization [{}]";
  public static final String MIGRATING_CHILD_AUTH = "Migrating child authorization for resource [{}]";
  public static final String SUCCESSFULLY_MIGRATED_CHILD_AUTH = "Successfully migrated child authorization for resource [{}]";
  public static final String SUCCESSFULLY_MIGRATED_AUTH = "Successfully migrated authorization [{}]";
  public static final String SKIPPED_AUTH = "Authorization with ID [{}] was skipped: {}";
  public static final String FOUND_DEFINITIONS_IN_DEPLOYMENT = "Found {} definitions for deployment [{}]";
  public static final String FOUND_CMMN_IN_DEPLOYMENT = "Found {} CMMN resources for deployment [{}], but CMMN is not supported in Camunda 8";
  public static final String STARTING_MIGRATION_OF_ENTITIES = "Starting migration of {} entities";
  public static final String FETCHING_LATEST_ID = "Fetching most recently migrated {} ID";
  public static final String LATEST_ID = "Latest migrated {} ID: {}";

  // Failure reasons constants
  public static final String FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED = "GLOBAL and REVOKE authorization types are not supported";
  public static final String FAILURE_OWNER_NOT_EXISTS = "User or group does not exist in C8";
  public static final String FAILURE_UNSUPPORTED_RESOURCE_TYPE = "Resource type [%s] is not supported";
  public static final String FAILURE_UNSUPPORTED_PERMISSION_TYPE = "Permission type [%s] is not supported for resource type [%s]";
  public static final String FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID = "Specific resource ID [%s] is not supported for resource type [%s], only wildcard is allowed";
  public static final String FAILURE_UNSUPPORTED_RESOURCE_ID = "Resource ID [%s] is not supported for resource type [%s]";
  public static final String FAILURE_UNEXPECTED_ERROR = "Unexpected error occurred while mapping authorization [%s]: %s";

  public static void logMigratingEntities(IdKeyMapper.TYPE type) {
    LOGGER.info(STARTING_MIGRATION_OF_ENTITIES, type.name().toLowerCase());
  }

  public static void logMigratingTenant(String tenantId) {
    LOGGER.debug(MIGRATING_TENANT, tenantId);
  }

  public static void logMigratedTenant(String tenantId) {
    LOGGER.info(SUCCESSFULLY_MIGRATED_TENANT, tenantId);
  }

  public static void logSkippedTenant(String tenantId) {
    LOGGER.warn(SKIPPED_TENANT, tenantId);
  }

  public static void logMigratingAuthorization(String authId) {
    LOGGER.debug(MIGRATING_AUTH, authId);
  }

  public static void logMigratingChildAuthorization(String resourceId) {
    LOGGER.debug(MIGRATING_CHILD_AUTH, resourceId);
  }

  public static void logMigratedChildAuthorization(String resourceId) {
    LOGGER.debug(SUCCESSFULLY_MIGRATED_CHILD_AUTH, resourceId);
  }

  public static void logMigratedAuthorization(String authId) {
    LOGGER.info(SUCCESSFULLY_MIGRATED_AUTH, authId);
  }

  public static void logSkippedAuthorization(String authId, String reason) {
    LOGGER.warn(SKIPPED_AUTH, authId, reason);
  }

  public static void foundDefinitionsInDeployment(int count, String deploymentId) {
    LOGGER.info(FOUND_DEFINITIONS_IN_DEPLOYMENT, count, deploymentId);
  }

  public static void foundCmmnInDeployment(int count, String deploymentId) {
    LOGGER.debug(FOUND_CMMN_IN_DEPLOYMENT, count, deploymentId);
  }

  public static void logFetchingLatestMigrated(IdKeyMapper.TYPE type) {
    LOGGER.debug(FETCHING_LATEST_ID, type.name().toLowerCase());
  }

  public static void logLatestId(IdKeyMapper.TYPE type, String id) {
    LOGGER.debug(LATEST_ID, type.name().toLowerCase(), Optional.ofNullable(id).orElse("none"));
  }
}
