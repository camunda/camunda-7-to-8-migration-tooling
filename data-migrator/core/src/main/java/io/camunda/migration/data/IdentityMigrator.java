/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static io.camunda.migration.data.MigratorMode.LIST_MIGRATED;
import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;

import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.migration.data.impl.DataSourceRegistry;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.identity.AuthorizationManager;
import io.camunda.migration.data.impl.identity.AuthorizationMappingResult;
import io.camunda.migration.data.impl.identity.C8Authorization;
import io.camunda.migration.data.impl.logging.IdentityMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.impl.util.ExceptionUtils;
import io.camunda.migration.data.impl.util.PrintUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentityMigrator {

  public static final long DEFAULT_KEY = 1L; // For entities that don't expose keys, we need a value different from null to mark them as migrated

  protected MigratorMode mode = MIGRATE;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected AuthorizationManager authorizationManager;

  @Autowired
  protected DataSourceRegistry dataSourceRegistry;

  public void start() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.IDENTITY);
      if (LIST_SKIPPED.equals(mode)) {
        listSkippedIdentityEntities();
      } else if (LIST_MIGRATED.equals(mode)) {
        listMigratedIdentityEntities();
      } else {
        migrate();
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode == null ? MIGRATE : mode;
  }

  protected void migrate() {
    migrateUsers();
    migrateGroups();
    migrateTenants();
    migrateAuthorizations();
  }

  protected void listSkippedIdentityEntities() {
    IdKeyMapper.IDENTITY_TYPES.forEach(type -> {
      PrintUtils.printSkippedInstancesHeader(dbClient.countSkippedByType(type), type);
      dbClient.listSkippedEntitiesByType(type);
    });
  }

  protected void listMigratedIdentityEntities() {
    IdKeyMapper.IDENTITY_TYPES.forEach(this::printMigratedEntitiesForType);
  }

  protected void printMigratedEntitiesForType(IdKeyMapper.TYPE type) {
    Long count = dbClient.countMigratedByType(type);
    if (type == IdKeyMapper.TYPE.TENANT) {
      PrintUtils.printMigratedC7IdsHeader(count, type);
      if (count > 0) {
        dbClient.listMigratedC7IdsByType(type);
      }
    } else {
      PrintUtils.printMigratedMappingsHeader(count, type);
      dbClient.listMigratedMappingsByType(type);
    }
  }

  protected void migrateUsers() {
    IdentityMigratorLogs.logMigratingEntities(IdKeyMapper.TYPE.USER);
    var txTemplate = dataSourceRegistry.getMigratorTxTemplate();
    fetchUsersToMigrate(user -> txTemplate.executeWithoutResult(status -> migrateUser(user)));
  }

  protected void migrateGroups() {
    IdentityMigratorLogs.logMigratingEntities(IdKeyMapper.TYPE.GROUP);
    var txTemplate = dataSourceRegistry.getMigratorTxTemplate();
    fetchGroupsToMigrate(group -> txTemplate.executeWithoutResult(status -> migrateGroup(group)));
  }

  protected void migrateTenants() {
    IdentityMigratorLogs.logMigratingEntities(IdKeyMapper.TYPE.TENANT);
    var txTemplate = dataSourceRegistry.getMigratorTxTemplate();
    fetchTenantsToMigrate(tenant -> txTemplate.executeWithoutResult(status -> migrateTenant(tenant)));
  }

  protected void migrateAuthorizations() {
    IdentityMigratorLogs.logMigratingEntities(IdKeyMapper.TYPE.AUTHORIZATION);

    var txTemplate = dataSourceRegistry.getMigratorTxTemplate();
    fetchAuthorizationsToMigrate(authorization ->
        txTemplate.executeWithoutResult(status -> migrateAuthorization(authorization)));
  }

  protected void migrateUser(User user) {
    try {
      IdentityMigratorLogs.logMigratingUser(user.getId());
      c8Client.createUser(user);
      Thread.sleep(1000); // test - to ensure that created users are visible when migrating memberships right after
      IdentityMigratorLogs.logMigratedUser(user.getId());
      saveRecord(IdKeyMapper.TYPE.USER, user.getId(), DEFAULT_KEY);
    } catch (MigratorException e) {
      markUserAsSkipped(user.getId(), e.getMessage());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  protected void migrateGroup(Group group) {
    try {
      IdentityMigratorLogs.logMigratingGroup(group);
      c8Client.createGroup(group);
      IdentityMigratorLogs.logMigratedGroup(group);
      saveRecord(IdKeyMapper.TYPE.GROUP, group.getId(), DEFAULT_KEY);
    } catch (MigratorException e) {
      markGroupAsSkipped(group, e.getMessage());
      return; // Only migrate memberships if group migration was successful
    }
    migrateGroupMemberships(group.getId());
  }

  protected void migrateTenant(Tenant tenant) {
    try {
      IdentityMigratorLogs.logMigratingTenant(tenant);
      c8Client.createTenant(tenant);
      IdentityMigratorLogs.logMigratedTenant(tenant);
      saveRecord(IdKeyMapper.TYPE.TENANT, tenant.getId(), DEFAULT_KEY);
    } catch (MigratorException e) {
      markTenantAsSkipped(tenant, e.getMessage());
      return; // Only migrate memberships if tenant migration was successful
    }
    migrateTenantMemberships(tenant.getId());
  }

  protected void migrateAuthorization(Authorization authorization) {
    IdentityMigratorLogs.logMigratingAuthorization(authorization);
    AuthorizationMappingResult mappingResult = authorizationManager.mapAuthorization(authorization);

    if (mappingResult.isSuccess()) {
      List<CreateAuthorizationResponse> migratedAuths = new ArrayList<>();
      try {
        if (mappingResult.isSingleAuth()) {
          migratedAuths.add(c8Client.createAuthorization(authorization.getId(), mappingResult.getC8Authorizations().getFirst()));
        } else {
          List<C8Authorization> authsToMigrate = mappingResult.getC8Authorizations();
          for (C8Authorization auth : authsToMigrate) {
            IdentityMigratorLogs.logMigratingChildAuthorization(auth.resourceId());
            CreateAuthorizationResponse response = c8Client.createAuthorization(authorization.getId(), auth);
            migratedAuths.add(response);
            IdentityMigratorLogs.logMigratedChildAuthorization(auth.resourceId());
          }
        }
        IdentityMigratorLogs.logMigratedAuthorization(authorization);
        saveRecord(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), migratedAuths.getFirst().getAuthorizationKey());
      } catch (MigratorException e) {
        markAuthorizationAsSkipped(authorization, e.getMessage());
      }
    } else {
      markAuthorizationAsSkipped(authorization, mappingResult.getReason());
    }
  }

  protected void migrateGroupMemberships(String groupId) {
    IdentityMigratorLogs.logMigratingGroupMemberships(groupId);
    List<User> userMemberships = c7Client.findUsersForGroup(groupId);
    for (User user : userMemberships) {
      IdentityMigratorLogs.logMigratingGroupMembership(groupId, user.getId());
      try {
        c8Client.createGroupAssignment(groupId, user.getId());
        IdentityMigratorLogs.logMigratedGroupMembership(groupId, user.getId());
      } catch (MigratorException e) {
        IdentityMigratorLogs.logCannotMigrateGroupMembership(groupId, user.getId(), e.getMessage());
      }
    }
  }

  protected void migrateTenantMemberships(String tenantId) {
    IdentityMigratorLogs.logMigratingTenantMemberships(tenantId);

    // migrate user tenant memberships
    List<User> userMemberships = c7Client.findUsersForTenant(tenantId);
    for (User user : userMemberships) {
      IdentityMigratorLogs.logMigratingTenantMembership(tenantId, OwnerType.USER.name(), user.getId());
      try {
        c8Client.createUserTenantAssignment(tenantId, user.getId());
        IdentityMigratorLogs.logMigratedTenantMembership(tenantId, OwnerType.USER.name(), user.getId());
      } catch (MigratorException e) {
        IdentityMigratorLogs.logCannotMigrateTenantMembership(tenantId, OwnerType.USER.name(), user.getId(), e.getMessage());
      }
    }

    // migrate group tenant memberships
    List<Group> groupMemberships = c7Client.findGroupsForTenant(tenantId);
    for (Group group : groupMemberships) {
      IdentityMigratorLogs.logMigratingTenantMembership(tenantId, OwnerType.GROUP.name(), group.getId());
      try {
        c8Client.createGroupTenantAssignment(tenantId, group.getId());
        IdentityMigratorLogs.logMigratedTenantMembership(tenantId, OwnerType.GROUP.name(), group.getId());
      } catch (MigratorException e) {
        IdentityMigratorLogs.logCannotMigrateTenantMembership(tenantId, OwnerType.USER.name(), group.getId(), e.getMessage());
      }
    }
  }

  protected void markAsSkipped(Authorization authorization, String reason) {
    IdentityMigratorLogs.logSkippedAuthorization(authorization, reason);
    markAsSkipped(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), reason);
  }

  protected void markUserAsSkipped(String username, String reason) {
    IdentityMigratorLogs.logSkippedUser(username, reason);
    saveRecord(IdKeyMapper.TYPE.USER, username, null);
  }

  protected void markGroupAsSkipped(Group group, String reason) {
    IdentityMigratorLogs.logSkippedGroup(group, reason);
    saveRecord(IdKeyMapper.TYPE.GROUP, group.getId(), null);
  }

  protected void markTenantAsSkipped(Tenant tenant, String reason) {
    IdentityMigratorLogs.logSkippedTenant(tenant, reason);
    markAsSkipped(IdKeyMapper.TYPE.TENANT, tenant.getId(), reason);
  }

  protected void markAsSkipped(IdKeyMapper.TYPE type, String c7Id, String reason) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateSkipReason(c7Id, type, reason);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, (Long) null, null, type, reason);
    }
  }

  protected void markAuthorizationAsSkipped(Authorization authorization, String reason) {
    IdentityMigratorLogs.logSkippedAuthorization(authorization, reason);
    if (MIGRATE.equals(mode)) {
      saveRecord(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), null);
    }
  }

  protected void saveRecord(IdKeyMapper.TYPE type, String c7Id, Long c8Key) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Id, c8Key, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, c8Key, type);
    }
  }

  protected void fetchUsersToMigrate(Consumer<User> userConsumer) {
    if (RETRY_SKIPPED.equals(mode)) {
      fetchAndHandleSkippedUsers(userConsumer);
    } else {
      IdentityMigratorLogs.logFetchingLatestMigrated(IdKeyMapper.TYPE.USER);
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.USER);
      IdentityMigratorLogs.logLatestId(IdKeyMapper.TYPE.USER, latestId);
      c7Client.fetchAndHandleUsers(userConsumer, latestId);
    }
  }

  protected void fetchGroupsToMigrate(Consumer<Group> groupConsumer) {
    if (RETRY_SKIPPED.equals(mode)) {
      fetchAndHandleSkippedGroups(groupConsumer);
    } else {
      IdentityMigratorLogs.logFetchingLatestMigrated(IdKeyMapper.TYPE.GROUP);
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.GROUP);
      IdentityMigratorLogs.logLatestId(IdKeyMapper.TYPE.GROUP, latestId);
      c7Client.fetchAndHandleGroups(groupConsumer, latestId);
    }
  }

  protected void fetchTenantsToMigrate(Consumer<Tenant> tenantConsumer) {
    if (RETRY_SKIPPED.equals(mode)) {
      fetchAndHandleSkippedTenants(tenantConsumer);
    } else {
      IdentityMigratorLogs.logFetchingLatestMigrated(IdKeyMapper.TYPE.TENANT);
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.TENANT);
      IdentityMigratorLogs.logLatestId(IdKeyMapper.TYPE.TENANT, latestId);
      c7Client.fetchAndHandleTenants(tenantConsumer, latestId);
    }
  }

  protected void fetchAuthorizationsToMigrate(Consumer<Authorization> authorizationConsumer) {
    if (RETRY_SKIPPED.equals(mode)) {
      fetchAndHandleSkippedAuthorizations(authorizationConsumer);
    } else {
      IdentityMigratorLogs.logFetchingLatestMigrated(IdKeyMapper.TYPE.AUTHORIZATION);
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.AUTHORIZATION);
      IdentityMigratorLogs.logLatestId(IdKeyMapper.TYPE.AUTHORIZATION, latestId);
      c7Client.fetchAndHandleAuthorizations(authorizationConsumer, latestId);
    }
  }

  protected void fetchAndHandleSkippedAuthorizations(Consumer<Authorization> callback) {
    dbClient.fetchAndHandleSkippedForType(IdKeyMapper.TYPE.AUTHORIZATION, (IdKeyDbModel skipped) -> {
      String authorizationId = skipped.getC7Id();
      Authorization authorization = c7Client.getAuthorization(authorizationId);
      if (authorization != null) {
        callback.accept(authorization);
      } else {
        IdentityMigratorLogs.logMissingAuthorization(authorizationId);
      }
    });
  }

  protected void fetchAndHandleSkippedUsers(Consumer<User> callback) {
    dbClient.fetchAndHandleSkippedForType(IdKeyMapper.TYPE.USER, (IdKeyDbModel skipped) -> {
      String userId = skipped.getC7Id();
      User user = c7Client.getUser(userId);
      if (user != null) {
        callback.accept(user);
      }
    });
  }

  protected void fetchAndHandleSkippedGroups(Consumer<Group> callback) {
    dbClient.fetchAndHandleSkippedForType(IdKeyMapper.TYPE.GROUP, (IdKeyDbModel skipped) -> {
      String groupId = skipped.getC7Id();
      Group group = c7Client.getGroup(groupId);
      if (group != null) {
        callback.accept(group);
      }
    });
  }

  protected void fetchAndHandleSkippedTenants(Consumer<Tenant> callback) {
    dbClient.fetchAndHandleSkippedForType(IdKeyMapper.TYPE.TENANT, (IdKeyDbModel skipped) -> {
      String tenantId = skipped.getC7Id();
      Tenant tenant = c7Client.getTenant(tenantId);
      if (tenant != null) {
        callback.accept(tenant);
      }
    });
  }
}
