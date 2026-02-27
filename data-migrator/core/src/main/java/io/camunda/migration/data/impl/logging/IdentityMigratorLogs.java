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
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.impl.util.ResourceTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentityMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(IdentityMigrator.class);

  // Event log constants
  public static final String MIGRATING_TENANT = "Migrating tenant [{}] (name: {})";
  public static final String MIGRATING_TENANT_MEMBERSHIPS = "Migrating tenant memberships for tenant [{}]";
  public static final String MIGRATING_TENANT_MEMBERSHIP = "Migrating tenant membership for tenant [{}] and {} [{}]";
  public static final String MIGRATED_TENANT_MEMBERSHIP = "Successfully migrated tenant membership for tenant [{}] and {} [{}]";
  public static final String CANNOT_MIGRATE_TENANT_MEMBERSHIP = "There was an error while migrating tenant membership for tenant [{}] and {} [{}]: {}";
  public static final String SUCCESSFULLY_MIGRATED_TENANT = "Successfully migrated tenant [{}] (name: {})";
  public static final String SKIPPED_TENANT = "Tenant with ID [{}] (name: {}) was skipped: {}";
  public static final String MIGRATING_AUTH = "Migrating authorization [{}] for {} [{}] on {} [{}]";
  public static final String MIGRATING_CHILD_AUTH = "Migrating child authorization for resource [{}]";
  public static final String SUCCESSFULLY_MIGRATED_CHILD_AUTH = "Successfully migrated child authorization for resource [{}]";
  public static final String SUCCESSFULLY_MIGRATED_AUTH = "Successfully migrated authorization [{}] for {} [{}] on {} [{}]";
  public static final String SKIPPED_AUTH = "Authorization with ID [{}] for {} [{}] on {} [{}] was skipped: {}";
  public static final String FOUND_DEFINITIONS_IN_DEPLOYMENT = "Found {} definitions for deployment [{}]";
  public static final String FOUND_CMMN_IN_DEPLOYMENT = "Found {} CMMN resources for deployment [{}], but CMMN is not supported in Camunda 8";
  public static final String STARTING_MIGRATION_OF_ENTITIES = "Starting migration of {} entities";
  public static final String FETCHING_LATEST_ID = "Fetching most recently migrated {} ID";
  public static final String LATEST_ID = "Latest migrated {} ID: {}";
  public static final String MISSING_AUTHORIZATION = "Authorization with ID {} can no longer be found, it might have been removed";

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

  public static void logMigratingTenant(Tenant tenant) {
    LOGGER.debug(MIGRATING_TENANT, tenant.getId(), tenant.getName());
  }

  public static void logMigratedTenant(Tenant tenant) {
    LOGGER.info(SUCCESSFULLY_MIGRATED_TENANT, tenant.getId(), tenant.getName());
  }

  public static void logSkippedTenant(Tenant tenant, String reason) {
    LOGGER.warn(SKIPPED_TENANT, tenant.getId(), tenant.getName(), reason);
  }

  public static void logMigratingAuthorization(Authorization authorization) {
    LOGGER.debug(MIGRATING_AUTH, authorization.getId(), ownerType(authorization), ownerId(authorization),
        resourceTypeName(authorization), authorization.getResourceId());
  }

  public static void logMigratingChildAuthorization(String resourceId) {
    LOGGER.debug(MIGRATING_CHILD_AUTH, resourceId);
  }

  public static void logMigratedChildAuthorization(String resourceId) {
    LOGGER.debug(SUCCESSFULLY_MIGRATED_CHILD_AUTH, resourceId);
  }

  public static void logMigratedAuthorization(Authorization authorization) {
    LOGGER.info(SUCCESSFULLY_MIGRATED_AUTH, authorization.getId(), ownerType(authorization), ownerId(authorization),
        resourceTypeName(authorization), authorization.getResourceId());
  }

  public static void logSkippedAuthorization(Authorization authorization, String reason) {
    LOGGER.warn(SKIPPED_AUTH, authorization.getId(), ownerType(authorization), ownerId(authorization),
        resourceTypeName(authorization), authorization.getResourceId(), reason);
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

  public static void logMissingAuthorization(String authId) {
    LOGGER.info(MISSING_AUTHORIZATION, authId);
  }

  public static void logMigratingTenantMemberships(String tenantId) {
    LOGGER.info(MIGRATING_TENANT_MEMBERSHIPS, tenantId);
  }

  public static void logMigratingTenantMembership(String tenantId, String type, String userOrGroupId) {
    LOGGER.info(MIGRATING_TENANT_MEMBERSHIP, tenantId, type, userOrGroupId);
  }

  public static void logMigratedTenantMembership(String tenantId, String type, String userOrGroupId) {
    LOGGER.info(MIGRATED_TENANT_MEMBERSHIP, tenantId, type, userOrGroupId);
  }

  public static void logCannotMigrateTenantMembership(String tenantId, String type, String userOrGroupId, String reason) {
    LOGGER.warn(CANNOT_MIGRATE_TENANT_MEMBERSHIP, tenantId, type, userOrGroupId, reason);
  }

  private static String ownerType(Authorization authorization) {
    return StringUtils.isNotBlank(authorization.getUserId()) ? "user" : "group";
  }

  private static String ownerId(Authorization authorization) {
    return StringUtils.isNotBlank(authorization.getUserId()) ? authorization.getUserId() : authorization.getGroupId();
  }

  private static String resourceTypeName(Authorization authorization) {
    return ResourceTypeUtil.getResourceByType(authorization.getResourceType()).resourceName();
  }
}
