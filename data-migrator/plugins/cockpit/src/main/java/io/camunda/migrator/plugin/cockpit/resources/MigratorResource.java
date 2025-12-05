/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.plugin.cockpit.resources;

import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.plugin.cockpit.MigratorQueryService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import jakarta.ws.rs.GET;

import org.camunda.bpm.cockpit.plugin.resource.AbstractCockpitPluginResource;

public class MigratorResource extends AbstractCockpitPluginResource {

  public MigratorResource(String engineName) {
    super(engineName);
  }

  @GET
  @Path("/skipped")
  @Produces("application/json")
  public List<IdKeyDbModel> getSkipped(@QueryParam("type") String type,
                                       @QueryParam("offset") int offset,
                                       @QueryParam("limit") int limit) {
    var parameters = new HashMap<String, Object>();
    parameters.put("type", type);
    parameters.put("offset", offset);
    parameters.put("limit", limit);
    return getCommandExecutor().executeCommand(new MigratorQueryService<>(parameters,
        (params, commandContext) -> (List<IdKeyDbModel>) commandContext.getDbSqlSession()
            .selectList("io.camunda.migrator.impl.persistence.IdKeyMapper.findSkippedByType", params)));
  }

  @GET
  @Path("/skipped/count")
  @Produces(MediaType.TEXT_PLAIN)
  public Long getSkippedCount(@QueryParam("type") String type) {
    var parameters = new HashMap<String, Object>();
    parameters.put("type", type);
    return getCommandExecutor().executeCommand(new MigratorQueryService<>(parameters,
        (params, commandContext) -> (Long) commandContext.getDbSqlSession()
            .selectOne("io.camunda.migrator.impl.persistence.IdKeyMapper.countSkippedByType", parameters)));
  }

  @GET
  @Path("/migrated")
  @Produces("application/json")
  public List<IdKeyDbModel> getMigrated(@QueryParam("type") String type,
                                       @QueryParam("offset") int offset,
                                       @QueryParam("limit") int limit) {
    var parameters = new HashMap<String, Object>();
    parameters.put("type", type);
    parameters.put("offset", offset);
    parameters.put("limit", limit);
    return getCommandExecutor().executeCommand(new MigratorQueryService<>(parameters,
        (params, commandContext) -> (List<IdKeyDbModel>) commandContext.getDbSqlSession()
            .selectList("io.camunda.migrator.impl.persistence.IdKeyMapper.findMigratedByType", params)));
  }

  @GET
  @Path("/migrated/count")
  @Produces(MediaType.TEXT_PLAIN)
  public Long getMigratedCount(@QueryParam("type") String type) {
    var parameters = new HashMap<String, Object>();
    parameters.put("type", type);
    return getCommandExecutor().executeCommand(new MigratorQueryService<>(parameters,
        (params, commandContext) -> (Long) commandContext.getDbSqlSession()
            .selectOne("io.camunda.migrator.impl.persistence.IdKeyMapper.countMigratedByType", parameters)));
  }

}
