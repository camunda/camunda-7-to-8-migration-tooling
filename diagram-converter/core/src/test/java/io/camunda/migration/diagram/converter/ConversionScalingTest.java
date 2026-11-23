/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.jupiter.api.Test;

/**
 * Regression test for https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/3695: the
 * check and conversion phases looked up element check results by scanning the full result list per
 * element, which made conversion time quadratic in model size.
 */
public class ConversionScalingTest {
  private static final int SMALL = 500;
  private static final int LARGE = SMALL * 4;

  @Test
  void conversionTimeShouldGrowRoughlyLinearlyWithModelSize() {
    // warm up the JIT so the measurements compare steady-state behaviour
    convert(SMALL);
    convert(SMALL);

    long small = bestOf(3, SMALL);
    long large = bestOf(3, LARGE);
    double ratio = (double) large / small;

    // 4x the elements: linear growth gives ~4x, quadratic growth ~16x
    assertThat(ratio)
        .as(
            "time for %d rules (%d ms) vs %d rules (%d ms)",
            LARGE, large / 1_000_000, SMALL, small / 1_000_000)
        .isLessThan(8.0);
  }

  private static long bestOf(int runs, int rules) {
    long best = Long.MAX_VALUE;
    for (int i = 0; i < runs; i++) {
      best = Math.min(best, convert(rules));
    }
    return best;
  }

  private static long convert(int rules) {
    DmnModelInstance model =
        Dmn.readModelFromStream(
            new ByteArrayInputStream(decisionTable(rules).getBytes(StandardCharsets.UTF_8)));
    DiagramConverter converter = DiagramConverterFactory.getInstance().get();
    ConverterProperties properties = ConverterPropertiesFactory.getInstance().get();
    long start = System.nanoTime();
    DiagramCheckResult result = converter.check("table.dmn", model, properties);
    long elapsed = System.nanoTime() - start;
    assertThat(result.getResults()).isNotEmpty();
    return elapsed;
  }

  private static String decisionTable(int rules) {
    int cols = 10;
    StringBuilder sb =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\""
                + " xmlns:modeler=\"http://camunda.org/schema/modeler/1.0\" id=\"drd\""
                + " name=\"DRD\" namespace=\"http://camunda.org/schema/1.0/dmn\""
                + " modeler:executionPlatform=\"Camunda Platform\""
                + " modeler:executionPlatformVersion=\"7.17.0\">"
                + "<decision id=\"big\" name=\"big\"><decisionTable id=\"t\" hitPolicy=\"FIRST\">");
    for (int c = 0; c < cols; c++) {
      sb.append(
          "<input id=\"in%d\"><inputExpression id=\"ie%d\" typeRef=\"string\"><text>v%d</text></inputExpression></input>"
              .formatted(c, c, c));
    }
    sb.append("<output id=\"o1\" name=\"rate\" typeRef=\"double\"/>")
        .append("<output id=\"o2\" name=\"rateId\" typeRef=\"string\"/>");
    for (int r = 0; r < rules; r++) {
      sb.append("<rule id=\"r%d\">".formatted(r));
      for (int c = 0; c < cols; c++) {
        sb.append(
            "<inputEntry id=\"r%d_i%d\"><text>\"x%d\"</text></inputEntry>".formatted(r, c, r % 7));
      }
      sb.append("<outputEntry id=\"r%d_o1\"><text>0.5</text></outputEntry>".formatted(r))
          .append(
              "<outputEntry id=\"r%d_o2\"><text>\"R%d\"</text></outputEntry></rule>"
                  .formatted(r, r));
    }
    return sb.append("</decisionTable></decision></definitions>").toString();
  }
}
