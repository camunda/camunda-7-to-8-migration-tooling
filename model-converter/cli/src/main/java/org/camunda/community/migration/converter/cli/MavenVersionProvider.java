/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.cli;

import picocli.CommandLine.IVersionProvider;

public class MavenVersionProvider implements IVersionProvider {
  @Override
  public String[] getVersion() throws Exception {
    return new String[] {this.getClass().getPackage().getImplementationVersion()};
  }
}
