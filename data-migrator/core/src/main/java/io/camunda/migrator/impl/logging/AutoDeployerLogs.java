/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import java.nio.file.Path;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoDeployerLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AutoDeployerLogs.class);

  public static final String ATTEMPTING_DEPLOYMENT = "Attempting deployment of files [{}]";
  public static final String SUCCESSFULLY_DEPLOYED = "Successfully deployed all files";

  public static void logAttemptingDeployment(Set<Path> files) {
    LOGGER.debug(ATTEMPTING_DEPLOYMENT, files);
  }

  public static void successfullyDeployed() {
    LOGGER.debug(SUCCESSFULLY_DEPLOYED);
  }

}
