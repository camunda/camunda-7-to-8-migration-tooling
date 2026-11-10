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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.camunda.bpm.dmn.engine.DmnDecision;
import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.DmnEngineConfiguration;
import org.camunda.bpm.model.dmn.Dmn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Uses the Camunda 7 DMN engine as the oracle: each scenario is evaluated by Camunda 7, and the
 * decision whose output the probe expression actually read must be the decision the converter names
 * in its finding.
 *
 * <p>Every scenario has a probe decision {@code Sub} whose input expression (id {@code probe})
 * reads the bare name {@code uid}. The providers output {@code uid} as {@code "from <id>"}; the
 * probe passes the value it read on, so the top-level decision's result shows which provider
 * Camunda 7 resolved.
 */
class RequiredDecisionOutputReferenceCamunda7Test {
  private static final Pattern PROVIDER = Pattern.compile("decision '([^']+)'");
  private static DmnEngine camunda7;

  @BeforeAll
  static void buildEngine() {
    camunda7 = DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine();
  }

  /**
   * @param requirements {@code decision=required,required} separated by {@code ;}, in evaluation
   *     order of the requirements; the first decision is the top-level decision
   * @param providers decisions that output {@code uid}
   * @param expected the decision Camunda 7 reads {@code uid} from, or {@code none}
   */
  @ParameterizedTest(name = "{0} / providers {1} -> {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        // later requirement overwrites the earlier one
        "Top=Sub; Sub=A,B | A,B | B",
        "Top=Sub; Sub=B,A | A,B | A",
        // indirect requirement
        "Top=Sub; Sub=Mid; Mid=Base | Base | Base",
        // sibling evaluated before the probe
        "Top=A,Sub | A | A",
        // sibling evaluated after the probe isn't visible yet
        "Top=Sub,A | A | none",
        // sibling overwrites the output of the probe's own requirement
        "Top=Final,Sub; Final=Base; Sub=Base | Base,Final | Final",
        "Top=Sub,Final; Final=Base; Sub=Base | Base,Final | Base",
        // provider of a sibling's subtree, then the probe's own requirement
        "Top=X,Sub; X=A; Sub=B | A,B | B",
        "Top=Sub,X; X=A; Sub=B | A,B | B",
        // shared requirement is evaluated once, where it's first required
        "Top=Sub,X; Sub=A; X=B,A | A,B | A",
        "Top=X,Sub; X=A,B; Sub=A | A,B | B",
        // deep chain on one side, sibling on the other
        "Top=L1,Sub; L1=L2; L2=L3; L3=A; Sub=B | A,B | B",
        "Top=Sub,L1; L1=L2; L2=L3; L3=A; Sub=B | A,B | B",
        "Top=L1,Sub; L1=L2; L2=L3; L3=A | A | A"
      })
  void shouldNameTheDecisionCamunda7Reads(String requirements, String providers, String expected) {
    String xml = drd(parse(requirements), Set.of(providers.split(",")));

    assertThat(camunda7Provider(xml)).as("Camunda 7").isEqualTo(expected);
    assertThat(converterProvider(xml)).as("converter").isEqualTo(expected);
  }

  /**
   * Names that Camunda 8 binds, but to another value: Camunda 7 reads the output, not the whole
   * multi-output result or the result of the decision whose variable has that name.
   */
  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "multi-output table named like its output | Base + \"\" | Base,other |  | v-Base | Base.Base",
        "variable of another requirement | score | score | score | v-score | Base"
      })
  void shouldReportNameBoundToAnotherValueInCamunda8(
      String scenario,
      String expression,
      String baseOutputs,
      String otherVariable,
      String camunda7Value,
      String expectedReference) {
    StringBuilder outputs = new StringBuilder();
    StringBuilder entries = new StringBuilder();
    for (String output : baseOutputs.split(",")) {
      outputs.append("<output id=\"o_%1$s\" name=\"%1$s\" typeRef=\"string\"/>".formatted(output));
      entries.append(
          "<outputEntry id=\"oe_%1$s\"><text>\"v-%1$s\"</text></outputEntry>".formatted(output));
    }
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="drd" name="DRD" namespace="http://camunda.org/schema/1.0/dmn">
          <decision id="Base" name="Base">
            <decisionTable id="t_b">
              <input id="i_b"><inputExpression id="e_b" typeRef="string"><text>"in"</text></inputExpression></input>
              %s
              <rule id="r_b"><inputEntry id="ie_b"><text>-</text></inputEntry>%s</rule>
            </decisionTable>
          </decision>
          <decision id="Other" name="Other">
            %s
            <decisionTable id="t_o">
              <input id="i_o"><inputExpression id="e_o" typeRef="string"><text>"in"</text></inputExpression></input>
              <output id="o_o" name="x" typeRef="string"/>
              <rule id="r_o"><inputEntry id="ie_o"><text>-</text></inputEntry><outputEntry id="oe_o"><text>"v-x"</text></outputEntry></rule>
            </decisionTable>
          </decision>
          <decision id="Top" name="Top">
            <variable id="v_t" name="result"/>
            <informationRequirement id="q_b"><requiredDecision href="#Base"/></informationRequirement>
            <informationRequirement id="q_o"><requiredDecision href="#Other"/></informationRequirement>
            <literalExpression id="probe"><text>%s</text></literalExpression>
          </decision>
        </definitions>
        """
            .formatted(
                outputs,
                entries,
                otherVariable == null
                    ? ""
                    : "<variable id=\"v_o\" name=\"%s\"/>".formatted(otherVariable),
                expression.replace("\"", "&quot;"));

    DmnDecision top =
        camunda7.parseDecision(
            "Top", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    assertThat((Object) camunda7.evaluateDecision(top, Map.of()).getSingleResult().getSingleEntry())
        .as("Camunda 7")
        .isEqualTo(camunda7Value);
    assertThat(
            DiagramConverterFactory.getInstance()
                .get()
                .check(
                    "oracle.dmn",
                    Dmn.readModelFromStream(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
                    ConverterPropertiesFactory.getInstance().get())
                .getResults()
                .stream()
                .filter(r -> "probe".equals(r.getElementId()))
                .flatMap(r -> r.getMessages().stream())
                .map(ElementCheckMessage::getMessage)
                .filter(m -> m.contains("required decision"))
                .toList())
        .as("converter")
        .singleElement()
        .asString()
        .contains("decision 'Base'")
        .contains("Use '" + expectedReference + "' instead.");
  }

  private static String camunda7Provider(String xml) {
    DmnDecision top =
        camunda7.parseDecision(
            "Top", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    try {
      DmnDecisionResult result = camunda7.evaluateDecision(top, Map.of());
      Object value = result.getSingleResult().getEntry("result");
      return value == null ? "none" : value.toString().substring("from ".length());
    } catch (RuntimeException e) {
      // FEEL fails on a variable that isn't in the context
      return "none";
    }
  }

  private static String converterProvider(String xml) {
    DiagramCheckResult result =
        DiagramConverterFactory.getInstance()
            .get()
            .check(
                "oracle.dmn",
                Dmn.readModelFromStream(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
                ConverterPropertiesFactory.getInstance().get());
    List<String> providers = new ArrayList<>();
    result.getResults().stream()
        .filter(r -> "probe".equals(r.getElementId()))
        .flatMap(r -> r.getMessages().stream())
        .map(ElementCheckMessage::getMessage)
        .filter(m -> m.contains("references 'uid'"))
        .forEach(
            m -> {
              Matcher matcher = PROVIDER.matcher(m);
              assertThat(matcher.find()).as(m).isTrue();
              providers.add(matcher.group(1));
            });
    assertThat(providers).as("findings on the probe").hasSizeLessThanOrEqualTo(1);
    return providers.isEmpty() ? "none" : providers.get(0);
  }

  private static Map<String, List<String>> parse(String requirements) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (String entry : requirements.split(";")) {
      String[] parts = entry.trim().split("=");
      List<String> required =
          parts.length < 2 || parts[1].isBlank()
              ? List.of()
              : Arrays.stream(parts[1].split(",")).map(String::trim).toList();
      result.put(parts[0].trim(), required);
    }
    return result;
  }

  private static String drd(Map<String, List<String>> requirements, Set<String> providers) {
    Set<String> ids = new java.util.LinkedHashSet<>(requirements.keySet());
    requirements.values().forEach(ids::addAll);
    ids.add("Sub");
    StringBuilder xml =
        new StringBuilder(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="drd" name="DRD" namespace="http://camunda.org/schema/1.0/dmn">
            """);
    for (String id : ids) {
      StringBuilder reqs = new StringBuilder();
      for (String required : requirements.getOrDefault(id, List.of())) {
        reqs.append(
            "<informationRequirement id=\"q_%s_%s\"><requiredDecision href=\"#%s\"/></informationRequirement>"
                .formatted(id, required, required));
      }
      String input;
      String output;
      String outputEntry;
      if ("Top".equals(id)) {
        input = "seen";
        output = "result";
        outputEntry = "seen";
      } else if ("Sub".equals(id)) {
        input = "uid";
        output = "seen";
        outputEntry = "uid";
      } else {
        input = "\"in\"";
        output = providers.contains(id) ? "uid" : id + "_out";
        outputEntry = "\"from " + id + "\"";
      }
      String inputExpressionId = "Sub".equals(id) ? "probe" : "e_" + id;
      xml.append(
          """
          <decision id="%1$s" name="%1$s">%2$s
            <decisionTable id="t_%1$s">
              <input id="i_%1$s"><inputExpression id="%3$s" typeRef="string"><text>%4$s</text></inputExpression></input>
              <output id="o_%1$s" name="%5$s" typeRef="string"/>
              <rule id="r_%1$s"><inputEntry id="ie_%1$s"><text>-</text></inputEntry><outputEntry id="oe_%1$s"><text>%6$s</text></outputEntry></rule>
            </decisionTable>
          </decision>
          """
              .formatted(
                  id,
                  reqs,
                  inputExpressionId,
                  input.replace("\"", "&quot;"),
                  output,
                  outputEntry.replace("\"", "&quot;")));
    }
    return xml.append("</definitions>").toString();
  }
}
