/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl;

import io.camunda.migration.data.config.property.C8Properties;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.logging.AutoDeployerLogs;
import io.camunda.migration.data.impl.util.ExceptionUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AutoDeployer {

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected MigratorProperties migratorProperties;

  public void deploy() {
    Set<Path> deploymentResources = getDeploymentResources();
    AutoDeployerLogs.logAttemptingDeployment(deploymentResources);
    c8Client.deployResources(deploymentResources);
    AutoDeployerLogs.successfullyDeployed();
  }

  public Set<Path> getDeploymentResources() {
    C8Properties c8Props = migratorProperties.getC8();
    if (c8Props != null) {
      String deploymentDir = c8Props.getDeploymentDir();
      if (StringUtils.hasText(deploymentDir)) {
        Path resourceDir = Paths.get(deploymentDir);

        try (Stream<Path> stream = Files.walk(resourceDir)) {
          return stream
              .filter(file -> !Files.isDirectory(file))
              .filter(file -> !isHidden(file))
              .collect(Collectors.toSet());
        } catch (IOException e) {
          throw ExceptionUtils.wrapException("Error occurred: shutting down Data Migrator gracefully.", e);
        }
      }
    }

    return Collections.emptySet();
  }

  public static boolean isHidden(Path file) {
    try {
      return Files.isHidden(file);
    } catch (IOException e) {
      throw ExceptionUtils.wrapException("Error occurred: shutting down Data Migrator gracefully.", e);
    }
  }
}
