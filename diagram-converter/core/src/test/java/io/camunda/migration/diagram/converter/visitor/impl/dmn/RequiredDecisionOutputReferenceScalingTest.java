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
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checking a DRD must take time roughly linear in its size, both in the number of decisions (which
 * grows the number of outputs each decision can see) and in the number of rules per decision.
 */
class RequiredDecisionOutputReferenceScalingTest {

  /** 4x the size: linear growth gives ~4x, quadratic growth ~16x. */
  @ParameterizedTest(name = "{0}x{1} -> {2}x{3}")
  @CsvSource({"20, 25, 80, 25", "10, 100, 10, 400"})
  void checkTimeShouldGrowRoughlyLinearly(
      int smallDecisions, int smallRules, int largeDecisions, int largeRules) {
    // warm up the JIT so the measurements compare steady-state behaviour
    check(smallDecisions, smallRules);
    check(smallDecisions, smallRules);

    long small = bestOf(3, smallDecisions, smallRules);
    long large = bestOf(3, largeDecisions, largeRules);
    double ratio = (double) large / small;

    assertThat(ratio)
        .as(
            "time for %dx%d (%d ms) vs %dx%d (%d ms)",
            largeDecisions,
            largeRules,
            large / 1_000_000,
            smallDecisions,
            smallRules,
            small / 1_000_000)
        .isLessThan(8.0);
  }

  /**
   * Many decisions with few rules: what the resolution keeps must grow with what the decisions
   * reference, not with every output evaluated before them. Counted rather than timed, because at
   * this size the rest of the conversion dominates the time.
   */
  @Test
  void resolutionShouldGrowLinearlyWithDecisions() {
    int small = resolutionSize(100);
    int large = resolutionSize(400);

    assertThat((double) large / small)
        .as("entries for 400 decisions (%d) vs 100 decisions (%d)", large, small)
        .isLessThan(8.0);
  }

  private static int resolutionSize(int decisions) {
    DmnModelInstance model =
        Dmn.readModelFromStream(
            new ByteArrayInputStream(drd(decisions, 1).getBytes(StandardCharsets.UTF_8)));
    return RequiredDecisionOutputReferenceVisitor.resolutionSize(
        model.getDocument().getRootElement());
  }

  private static long bestOf(int runs, int decisions, int rules) {
    long best = Long.MAX_VALUE;
    for (int i = 0; i < runs; i++) {
      best = Math.min(best, check(decisions, rules));
    }
    return best;
  }

  private static long check(int decisions, int rules) {
    DmnModelInstance model =
        Dmn.readModelFromStream(
            new ByteArrayInputStream(drd(decisions, rules).getBytes(StandardCharsets.UTF_8)));
    long start = System.nanoTime();
    DiagramCheckResult result =
        DiagramConverterFactory.getInstance()
            .get()
            .check("scaling.dmn", model, ConverterPropertiesFactory.getInstance().get());
    long elapsed = System.nanoTime() - start;
    assertThat(result.getResults()).isNotEmpty();
    return elapsed;
  }

  /**
   * Decisions D0..Dn are siblings under one top-level decision, each requiring its predecessor.
   * Every rule references the outputs of the two previous decisions by bare name, one of them only
   * visible as a sibling's output.
   */
  private static String drd(int decisions, int rules) {
    StringBuilder sb =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" id=\"drd\""
                + " name=\"DRD\" namespace=\"http://camunda.org/schema/1.0/dmn\">");
    for (int d = 0; d < decisions; d++) {
      sb.append("<decision id=\"D%d\" name=\"D%d\">".formatted(d, d));
      if (d > 0) {
        sb.append(
            "<informationRequirement id=\"q%d\"><requiredDecision href=\"#D%d\"/></informationRequirement>"
                .formatted(d, d - 1));
      }
      String previous = d > 0 ? "o" + (d - 1) : "in";
      String beforePrevious = d > 1 ? "o" + (d - 2) : "in";
      sb.append(
          ("<decisionTable id=\"t%1$d\" hitPolicy=\"FIRST\">"
                  + "<input id=\"i%1$d\"><inputExpression id=\"e%1$d\" typeRef=\"string\">"
                  + "<text>%2$s</text></inputExpression></input>"
                  + "<output id=\"o%1$d\" name=\"o%1$d\" typeRef=\"string\"/>")
              .formatted(d, previous));
      for (int r = 0; r < rules; r++) {
        sb.append(
            ("<rule id=\"r%1$d_%2$d\"><inputEntry id=\"r%1$d_%2$d_i\"><text>\"x%2$d\"</text></inputEntry>"
                    + "<outputEntry id=\"r%1$d_%2$d_o\"><text>%3$s + %4$s + \"%2$d\"</text></outputEntry></rule>")
                .formatted(d, r, previous, beforePrevious));
      }
      sb.append("</decisionTable></decision>");
    }
    sb.append("<decision id=\"Top\" name=\"Top\"><variable id=\"vt\" name=\"Top\"/>");
    for (int d = 0; d < decisions; d++) {
      sb.append(
          "<informationRequirement id=\"qt%d\"><requiredDecision href=\"#D%d\"/></informationRequirement>"
              .formatted(d, d));
    }
    return sb.append("<literalExpression id=\"lt\"><text>o0</text></literalExpression></decision>")
        .append("</definitions>")
        .toString();
  }
}
