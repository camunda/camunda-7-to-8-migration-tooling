/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.bpmn;

import static io.camunda.migration.diagram.converter.bpmn.BpmnTestcaseUtils.wrapSnippetInProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.migration.diagram.converter.ConverterProperties;
import io.camunda.migration.diagram.converter.ConverterPropertiesFactory;
import io.camunda.migration.diagram.converter.DefaultConverterProperties;
import io.camunda.migration.diagram.converter.DiagramConverter;
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import io.camunda.migration.diagram.converter.bpmn.BpmnTestcaseLoader.BpmnConversionCase;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = BpmnDeploymentIT.TestApplication.class)
@CamundaSpringProcessTest
// Windows runners do not provide a Docker environment for Testcontainers.
@DisabledOnOs(OS.WINDOWS)
class BpmnDeploymentIT {
  private static final String DEPLOYMENT_PLATFORM_VERSION = "8.9";
  private static final Set<String> DEPLOYMENT_CASES =
      Set.of(
          "BPMN start event without execution listener", "Execution Listener on BPMN start event");

  @Autowired private CamundaClient camundaClient;

  @ParameterizedTest(name = "{0}")
  @MethodSource("loadDeploymentCases")
  void shouldDeployConvertedBpmn(BpmnConversionCase testCase) {
    BpmnModelInstance modelInstance = wrapSnippetInProcess(testCase.givenBpmn());
    DefaultConverterProperties defaultProperties = new DefaultConverterProperties();
    defaultProperties.setAppendDocumentation(false);
    defaultProperties.setPlatformVersion(DEPLOYMENT_PLATFORM_VERSION);
    ConverterProperties properties =
        ConverterPropertiesFactory.getInstance().merge(defaultProperties);

    DiagramConverter converter = DiagramConverterFactory.getInstance().get();
    converter.convert(modelInstance, properties);

    StringWriter writer = new StringWriter();
    converter.printXml(modelInstance.getDocument(), true, writer);
    io.camunda.zeebe.model.bpmn.BpmnModelInstance c8Model =
        io.camunda.zeebe.model.bpmn.Bpmn.readModelFromStream(
            new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8)));

    assertThat(
            camundaClient
                .newDeployResourceCommand()
                .addProcessModel(c8Model, testCase.name() + ".bpmn")
                .execute())
        .isNotNull();
  }

  static Stream<BpmnConversionCase> loadDeploymentCases() throws IOException {
    List<BpmnConversionCase> deploymentCases =
        BpmnConversionTest.loadConversionCases()
            .filter(testCase -> DEPLOYMENT_CASES.contains(testCase.name()))
            .toList();
    assertThat(deploymentCases)
        .extracting(BpmnConversionCase::name)
        .containsExactlyInAnyOrderElementsOf(DEPLOYMENT_CASES);
    return deploymentCases.stream();
  }

  @SpringBootApplication
  static class TestApplication {}
}
