/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import io.camunda.migrator.exception.MigratorException;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.logging.IdentityMigratorLogs;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.impl.util.ExceptionUtils;
import java.util.List;
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

  public void migrate() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.IDENTITY);
      String latestId = dbClient.findLatestIdByType(IdKeyMapper.TYPE.TENANT);
      List<Tenant> tenants = c7Client.fetchTenants(latestId);
      for (Tenant tenant : tenants) {
        migrateTenant(tenant);
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  private void migrateTenant(Tenant tenant) {
    try {
      IdentityMigratorLogs.logMigratingTenant(tenant.getId());
      c8Client.createTenant(tenant);
      IdentityMigratorLogs.logMigratedTenant(tenant.getId());
      saveRecord(tenant.getId(), DEFAULT_TENANT_KEY); // Tenants do not have keys, we need a value different from null
    } catch (MigratorException e) {
      IdentityMigratorLogs.logSkippedTenant(tenant.getId());
      saveRecord(tenant.getId(), null);
    }
  }

  private void saveRecord(String c7Id, Long c8Key) {
    dbClient.insert(c7Id, c8Key, IdKeyMapper.TYPE.TENANT);
  }
}
