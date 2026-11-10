/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.ConverterPropertiesFactory;
import io.camunda.migration.diagram.converter.DiagramCheckResult;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckMessage;
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.DmnEngineConfiguration;
import org.camunda.bpm.model.dmn.Dmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the complete required-decision report for a realistic DRD, so that any change in what is
 * reported, or how, shows up as a diff of {@code c7-style.expected.txt}.
 */
class RequiredDecisionOutputReferenceReportTest {
  private static final String DIR = "/required-decision-outputs/";
  private static final String LINK =
      "https://docs.camunda.io/docs/components/modeler/dmn/decision-requirements-graph/#required-decisions";

  /** The fixture must stay a DRD that Camunda 7 evaluates, with the values the report relies on. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        // Price requires Customer_Data, but reads the uid that Final_Eligibility overwrote
        "Quote | uid: U-C1-final, segment: gold = 45.0",
        // Audit_Log reads uid from Final_Eligibility here ...
        "Quote_Audit | audit U-C1-final",
        // ... and from Audit_Source here
        "Audit_Report | audit A-X"
      })
  void fixtureShouldBehaveAsDescribedInCamunda7(String decisionId, String expected) {
    DmnEngine camunda7 = DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine();
    Object result =
        camunda7
            .evaluateDecision(
                camunda7.parseDecision(decisionId, resource("c7-style.dmn")),
                Map.of("customerId", "C1", "auditId", "X"))
            .getSingleResult()
            .getSingleEntry();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldReportExpectedFindings() throws IOException {
    String expected =
        new String(resource("c7-style.expected.txt").readAllBytes(), StandardCharsets.UTF_8);

    assertThat(String.join("\n", report("c7-style.dmn")) + "\n").isEqualTo(expected);
  }

  @Test
  void shouldReportNothingForCamunda8StyleReferences() {
    assertThat(report("c8-style.dmn")).isEmpty();
  }

  /**
   * The visitor caches the resolution of the last DRD per thread. Checking different DRDs in a row
   * on one thread must give the same reports as checking each on a fresh thread.
   */
  @Test
  void shouldNotLeakResolutionBetweenConsecutiveChecks() {
    List<String> c7Fresh = onFreshThread("c7-style.dmn");
    List<String> c8Fresh = onFreshThread("c8-style.dmn");

    for (int i = 0; i < 2; i++) {
      assertThat(report("c8-style.dmn")).isEqualTo(c8Fresh);
      assertThat(report("c7-style.dmn")).isEqualTo(c7Fresh);
    }
    assertThat(c7Fresh).isNotEmpty();
  }

  private static List<String> onFreshThread(String file) {
    return CompletableFuture.supplyAsync(
            () -> report(file), r -> new Thread(r, "fresh-" + file).start())
        .join();
  }

  /** Required-decision findings, one {@code elementId | message} line each, in report order. */
  private static List<String> report(String file) {
    DiagramCheckResult result =
        DiagramConverterFactory.getInstance()
            .get()
            .check(
                file,
                Dmn.readModelFromStream(resource(file)),
                ConverterPropertiesFactory.getInstance().get());
    List<String> lines = new ArrayList<>();
    result
        .getResults()
        .forEach(
            r ->
                r.getMessages().stream()
                    .filter(m -> LINK.equals(m.getLink()))
                    .map(ElementCheckMessage::getMessage)
                    .forEach(m -> lines.add(r.getElementId() + " | " + m)));
    return lines;
  }

  private static InputStream resource(String file) {
    return RequiredDecisionOutputReferenceReportTest.class.getResourceAsStream(DIR + file);
  }
}
