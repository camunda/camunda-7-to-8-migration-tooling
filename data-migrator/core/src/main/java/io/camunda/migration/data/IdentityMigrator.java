/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;

import io.camunda.client.api.response.CreateAuthorizationResponse;
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
import org.camunda.bpm.engine.identity.Tenant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentityMigrator {

  public static final long DEFAULT_TENANT_KEY = 1L;

  protected MigratorMode mode = MigratorMode.MIGRATE;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected AuthorizationManager authorizationManager;

  public void migrate() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.IDENTITY);

      if (LIST_SKIPPED.equals(mode)) {
        listSkipped();
      } else {
        migrateTenants();
        migrateAuthorizations();
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  protected void listSkipped() {
    // tenants
    PrintUtils.printSkippedInstancesHeader(
        dbClient.countSkippedByType(IdKeyMapper.TYPE.TENANT),
        IdKeyMapper.TYPE.TENANT);
    dbClient.listSkippedEntitiesByType(IdKeyMapper.TYPE.TENANT);

    // authorizations
    PrintUtils.printSkippedInstancesHeader(
        dbClient.countSkippedByType(IdKeyMapper.TYPE.AUTHORIZATION),
        IdKeyMapper.TYPE.AUTHORIZATION);
    dbClient.listSkippedEntitiesByType(IdKeyMapper.TYPE.AUTHORIZATION);
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode == null ? MigratorMode.MIGRATE : mode;
  }

  protected void migrateTenants() {
    fetchTenantsToMigrate(this::migrateTenant);
  }

  protected void migrateAuthorizations() {
    fetchAuthorizationsToMigrate(this::migrateAuthorization);
  }

  protected void migrateTenant(Tenant tenant) {
    try {
      IdentityMigratorLogs.logMigratingTenant(tenant.getId());
      c8Client.createTenant(tenant);
      IdentityMigratorLogs.logMigratedTenant(tenant.getId());
      saveRecord(IdKeyMapper.TYPE.TENANT, tenant.getId(), DEFAULT_TENANT_KEY); // Tenants do not have keys, we need a value different from null
    } catch (MigratorException e) {
      markAsSkipped(IdKeyMapper.TYPE.TENANT, tenant.getId(), e.getMessage());
    }
  }

  protected void migrateAuthorization(Authorization authorization) {
    IdentityMigratorLogs.logMigratingAuthorization(authorization.getId());
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
        IdentityMigratorLogs.logMigratedAuthorization(authorization.getId());
        saveRecord(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), migratedAuths.getFirst().getAuthorizationKey());
      } catch (MigratorException e) {
        markAsSkipped(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), e.getMessage());
      }
    } else {
      markAsSkipped(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), mappingResult.getReason());
    }
  }

  protected void markAsSkipped(IdKeyMapper.TYPE type, String id, String reason) {
    switch (type) {
      case TENANT -> IdentityMigratorLogs.logSkippedTenant(id);
      case AUTHORIZATION -> IdentityMigratorLogs.logSkippedAuthorization(id, reason);
    }

    if (MIGRATE.equals(mode)) { // only mark as skipped in MIGRATE mode, if it's RETRY_SKIPPED, it's already marked as skipped
      saveRecord(type, id, null);
    }
  }

  protected void saveRecord(IdKeyMapper.TYPE type, String c7Id, Long c8Key) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Id, c8Key, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, c8Key, type);
    }
  }

  protected void fetchTenantsToMigrate(Consumer<Tenant> tenantConsumer) {
    if (mode == RETRY_SKIPPED) {
      fetchAndHandleSkippedTenants(tenantConsumer);
    } else {
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.TENANT);
      c7Client.fetchAndHandleTenants(tenantConsumer, latestId);
    }
  }

  protected void fetchAuthorizationsToMigrate(Consumer<Authorization> authorizationConsumer) {
    if (mode == MigratorMode.RETRY_SKIPPED) {
      fetchAndHandleSkippedAuthorizations(authorizationConsumer);
    } else {
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.AUTHORIZATION);
      c7Client.fetchAndHandleAuthorizations(authorizationConsumer, latestId);
    }
  }

  protected void fetchAndHandleSkippedAuthorizations(Consumer<Authorization> callback) {
    dbClient.fetchAndHandleSkippedForType(IdKeyMapper.TYPE.AUTHORIZATION, (IdKeyDbModel skipped) -> {
      String authorizationId = skipped.getC7Id();
      Authorization authorization = c7Client.getAuthorization(authorizationId);
      if (authorization != null) {
        callback.accept(authorization);
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
