/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ConvertEngineCommandHelpTest {

  @Test
  void shouldRenderPasswordAndAuthenticationLabelsInHelp() {
    String help = new CommandLine(new ConvertEngineCommand()).getUsageMessage();

    assertThat(help).contains("-p, --password=<password>");
    assertThat(help).contains("Password for Basic authentication");
    assertThat(help).contains("-u, --username=<username>");
    assertThat(help).contains("Username for Basic authentication");
    assertThat(help).contains("-t, --target-directory=<targetDirectory>");
  }
}
