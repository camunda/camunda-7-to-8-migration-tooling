/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.plugin.cockpit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.camunda.migrator.plugin.cockpit.resources.PluginRootResource;
import org.camunda.bpm.cockpit.plugin.spi.impl.AbstractCockpitPlugin;

public class MigratorPlugin extends AbstractCockpitPlugin {

  public static final String ID = "migrator-plugin";

  public String getId() {
    return ID;
  }

  @Override
  public Set<Class<?>> getResourceClasses() {
    Set<Class<?>> classes = new HashSet<>();

    classes.add(PluginRootResource.class);

    return classes;
  }

  @Override
  public List<String> getMappingFiles() {
    return Arrays.asList("mapper/IdKey.xml", "mapper/Commons.xml");
  }
}
