/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.plugin.cockpit.resources;

import io.camunda.migrator.plugin.cockpit.MigratorPlugin;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.camunda.bpm.cockpit.plugin.resource.AbstractCockpitPluginRootResource;

@Path("plugin/" + MigratorPlugin.ID)
public class PluginRootResource extends AbstractCockpitPluginRootResource {

  public PluginRootResource() {
    super(MigratorPlugin.ID);
  }

  @Path("{engineName}/migrator")
  public MigratorResource getSkippedResource(@PathParam("engineName") String engineName) {
    return subResource(new MigratorResource(engineName), engineName);
  }
}
