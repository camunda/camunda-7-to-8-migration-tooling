/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.migration.data.exception.IdentityMigratorException;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.identity.AuthorizationManager;
import io.camunda.migration.data.impl.identity.C8Authorization;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.logging.IdentityMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.impl.util.ExceptionUtils;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.identity.Tenant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentityMigrator {

  public static final long DEFAULT_TENANT_KEY = 1L;

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

  protected void migrateTenants() {
    String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.TENANT);
    List<Tenant> tenants = c7Client.fetchTenants(latestId);
    for (Tenant tenant : tenants) {
      migrateTenant(tenant);
    }
  }

  protected void migrateAuthorizations() {
    String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.AUTHORIZATION);
    List<Authorization> authorizations = c7Client.fetchAuthorizations(latestId);
    for (Authorization authorization : authorizations) {
      migrateAuthorization(authorization);
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

  private void migrateAuthorization(Authorization authorization) {
    IdentityMigratorLogs.logMigratingAuthorization(authorization.getId());
    Optional<C8Authorization> c8Auth = mapToC8Authorization(authorization);

    if (c8Auth.isPresent()) {
      try {
        CreateAuthorizationResponse migratedAuth = c8Client.createAuthorization(authorization.getId(), c8Auth.get());
        IdentityMigratorLogs.logMigratedAuthorization(authorization.getId());
        saveRecord(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), migratedAuth.getAuthorizationKey());
      } catch (MigratorException e) {
        markAsSkipped(authorization, e.getMessage());
      }
    }
  }

  protected Optional<C8Authorization> mapToC8Authorization(Authorization authorization) {
    if (authorization.getAuthorizationType() != Authorization.AUTH_TYPE_GRANT) {
      markAsSkipped(authorization, "GLOBAL and REVOKE authorization types are not supported");
      return Optional.empty();
    }

    if (!ownerExists(authorization.getUserId(), authorization.getGroupId())) {
      markAsSkipped(authorization, "User or group does not exist in C8");
      return Optional.empty();
    }

    return authorizationManager.mapAuthorization(authorization);
  }

protected void markAsSkipped(Authorization authorization, String reason) {
  IdentityMigratorLogs.logSkippedAuthorization(authorization.getId(), reason);
  saveRecord(IdKeyMapper.TYPE.AUTHORIZATION, authorization.getId(), null);
}

protected boolean ownerExists(String userId, String groupId) {
    Object userOrGroup = null;
    try {
      if (isNotBlank(userId)) {
        userOrGroup = c8Client.getUser(userId);
      } else if (isNotBlank(groupId)) {
        userOrGroup = c8Client.getGroup(groupId);
      }
      return userOrGroup != null;
    } catch (MigratorException e) {
      if (e.getCause() instanceof ProblemException pe && pe.details().getStatus() == 404) { // Not found
        return false;
      } else {
        throw new IdentityMigratorException("Cannot verify owner existence", e);
      }
    }
  }


  protected void saveRecord(IdKeyMapper.TYPE type, String c7Id, Long c8Key) {
    dbClient.insert(c7Id, c8Key, type);
  }
}
