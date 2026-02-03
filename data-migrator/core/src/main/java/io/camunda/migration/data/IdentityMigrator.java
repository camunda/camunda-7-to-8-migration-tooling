/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

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
import java.util.ArrayList;
import java.util.Date;
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
      migrateTenants();
      migrateAuthorizations();
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode == null ? MigratorMode.MIGRATE : mode;
  }

  protected void migrateTenants() {
    String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.TENANT);
    List<Tenant> tenants = c7Client.fetchTenants(latestId);
    for (Tenant tenant : tenants) {
      migrateTenant(tenant);
    }
  }

  protected void migrateAuthorizations() {
    fetchAuthorizationsToMigrate(this::migrateAuthorization);
  }

  protected void fetchAuthorizationsToMigrate(Consumer<Authorization> authorizationConsumer) {
    if (mode == MigratorMode.RETRY_SKIPPED) {
      fetchAndHandleSkippedAuthorizations(authorizationConsumer);
    } else {
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.AUTHORIZATION);
      c7Client.fetchAndHandleAuthorizations(authorizationConsumer, latestId);
    }
  }

  protected void migrateTenant(Tenant tenant) {
    try {
      IdentityMigratorLogs.logMigratingTenant(tenant.getId());
      c8Client.createTenant(tenant);
      IdentityMigratorLogs.logMigratedTenant(tenant.getId());
      saveRecord(IdKeyMapper.TYPE.TENANT, tenant.getId(), DEFAULT_TENANT_KEY); // Tenants do not have keys, we need a value different from null
    } catch (MigratorException e) {
      IdentityMigratorLogs.logSkippedTenant(tenant.getId());
      saveRecord(IdKeyMapper.TYPE.TENANT, tenant.getId(), null);
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
        markAsSkipped(authorization.getId(), e.getMessage());
      }
    } else {
      markAsSkipped(authorization.getId(), mappingResult.getReason());
    }
  }

  protected void markAsSkipped(String id, String reason) {
    IdentityMigratorLogs.logSkippedAuthorization(id, reason);
    if (MIGRATE.equals(mode)) { // only mark as skipped in MIGRATE mode, if it's RETRY_SKIPPED, it's already marked as skipped
      saveRecord(IdKeyMapper.TYPE.AUTHORIZATION, id, null);
    }
  }

  protected void saveRecord(IdKeyMapper.TYPE type, String c7Id, Long c8Key) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Id, c8Key, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, c8Key, type);
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
}
