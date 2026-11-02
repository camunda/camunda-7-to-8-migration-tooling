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
import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers every expression position in a decision (input expression, input entry, output entry,
 * literal expression) and every way a Camunda 7 required-decision output can be provided (multi-
 * and single-output tables, literal expressions, direct and indirect requirements, collisions).
 */
class RequiredDecisionOutputReferenceVisitorTest {
  private static final String LINK =
      "https://docs.camunda.io/docs/components/modeler/dmn/decision-requirements-graph/#required-decisions";

  /** The DRD from issue #3694. */
  private static final String ISSUE_DRD =
      """
      <decision id="Base" name="Base">
        <decisionTable id="t1" hitPolicy="FIRST">
          <input id="i1"><inputExpression id="e1" typeRef="string"><text>code</text></inputExpression></input>
          <output id="o1" name="rate" typeRef="double"/>
          <output id="o2" name="rateId" typeRef="string"/>
          <rule id="r1"><inputEntry id="a"><text>"A"</text></inputEntry><outputEntry id="b"><text>0.5</text></outputEntry><outputEntry id="c"><text>"R1"</text></outputEntry></rule>
        </decisionTable>
      </decision>
      <decision id="Middle" name="Middle">
        <informationRequirement id="q1"><requiredDecision href="#Base"/></informationRequirement>
        <decisionTable id="t2" hitPolicy="FIRST">
          <input id="i2"><inputExpression id="e2" typeRef="double"><text>rate</text></inputExpression></input>
          <output id="o3" name="factor" typeRef="double"/>
          <rule id="r2"><inputEntry id="d"><text>-</text></inputEntry><outputEntry id="f"><text>rate * 2</text></outputEntry></rule>
        </decisionTable>
      </decision>
      <decision id="Top" name="Top">
        <informationRequirement id="q2"><requiredDecision href="#Middle"/></informationRequirement>
        <decisionTable id="t3" hitPolicy="FIRST">
          <input id="i3"><inputExpression id="e3" typeRef="double"><text>factor</text></inputExpression></input>
          <output id="o4" name="result" typeRef="string"/>
          <rule id="r3"><inputEntry id="g"><text>-</text></inputEntry><outputEntry id="h"><text>rateId + ":" + string(factor)</text></outputEntry></rule>
        </decisionTable>
      </decision>
      """;

  @Test
  void shouldReportEveryBareReferenceOfTheIssueDrd() {
    Map<String, List<String>> messages = taskMessages(ISSUE_DRD);

    assertThat(messages)
        .containsOnlyKeys("e2", "f", "e3", "h")
        .containsEntry(
            "e2",
            List.of(
                "Expression 'rate' references 'rate', an output of the required decision 'Base'."
                    + " Camunda 8 does not expose the outputs of required decisions by their output"
                    + " name, so this reference evaluates to null. Use 'Base.rate' instead."))
        .containsEntry(
            "e3",
            List.of(
                "Expression 'factor' references 'factor', an output of the required decision"
                    + " 'Middle'. Camunda 8 does not expose the outputs of required decisions by"
                    + " their output name, so this reference evaluates to null. Use 'Middle'"
                    + " instead."));
    assertThat(messages.get("f")).singleElement().asString().contains("Use 'Base.rate' instead.");
    assertThat(messages.get("h"))
        .containsExactlyInAnyOrder(
            "Expression 'rateId + \":\" + string(factor)' references 'rateId', an output of the"
                + " decision 'Base', which this decision requires only indirectly. Camunda 8 only"
                + " exposes the results of direct requirements, and not by their output name, so"
                + " this reference evaluates to null. Add a requirement on 'Base' and use"
                + " 'Base.rateId' instead.",
            "Expression 'rateId + \":\" + string(factor)' references 'factor', an output of the"
                + " required decision 'Middle'. Camunda 8 does not expose the outputs of required"
                + " decisions by their output name, so this reference evaluates to null. Use"
                + " 'Middle' instead.");
  }

  @Test
  void shouldLinkToRequiredDecisionDocs() {
    assertThat(check(ISSUE_DRD).getResults())
        .flatExtracting(DiagramCheckResult.ElementCheckResult::getMessages)
        .filteredOn(m -> m.getMessage().contains("required decision"))
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.getLink()).isEqualTo(LINK));
  }

  @Test
  void shouldReportReferenceInInputEntryAndLiteralExpression() {
    String drd =
        provider("Base", "limit")
            + """
            <decision id="Rule" name="Rule">
              <informationRequirement id="q1"><requiredDecision href="#Base"/></informationRequirement>
              <decisionTable id="t2">
                <input id="i2"><inputExpression id="e2" typeRef="number"><text>amount</text></inputExpression></input>
                <output id="o2" name="ok" typeRef="boolean"/>
                <rule id="r2"><inputEntry id="ie"><text>&lt;= limit</text></inputEntry><outputEntry id="oe"><text>true</text></outputEntry></rule>
              </decisionTable>
            </decision>
            <decision id="Literal" name="Literal">
              <variable id="v1" name="doubled"/>
              <informationRequirement id="q2"><requiredDecision href="#Base"/></informationRequirement>
              <literalExpression id="le"><text>limit * 2</text></literalExpression>
            </decision>
            """;

    Map<String, List<String>> messages = taskMessages(drd);

    assertThat(messages).containsOnlyKeys("ie", "le");
    assertThat(messages.get("ie")).singleElement().asString().contains("Use 'Base' instead.");
    assertThat(messages.get("le")).singleElement().asString().contains("Use 'Base' instead.");
  }

  @Test
  void shouldSuggestVariableNameOfLiteralExpressionProvider() {
    String drd =
        """
        <decision id="Base" name="Base">
          <variable id="v1" name="baseRate"/>
          <literalExpression id="le1"><text>0.5</text></literalExpression>
        </decision>
        <decision id="Middle" name="Middle">
          <variable id="v2" name="middle"/>
          <informationRequirement id="q1"><requiredDecision href="#Base"/></informationRequirement>
          <literalExpression id="le2"><text>baseRate * 2</text></literalExpression>
        </decision>
        <decision id="Top" name="Top">
          <variable id="v3" name="top"/>
          <informationRequirement id="q2"><requiredDecision href="#Middle"/></informationRequirement>
          <literalExpression id="le3"><text>baseRate + middle</text></literalExpression>
        </decision>
        """;

    Map<String, List<String>> messages = taskMessages(drd);

    // baseRate in Middle and middle in Top are visible in Camunda 8 under the variable name
    assertThat(messages).containsOnlyKeys("le3");
    assertThat(messages.get("le3"))
        .singleElement()
        .asString()
        .contains("requires only indirectly")
        .contains("Add a requirement on 'Base' and use 'baseRate' instead.");
  }

  @Test
  void shouldReportAmbiguousReference() {
    String drd =
        provider("A", "score")
            + provider("B", "score")
            + """
            <decision id="Top" name="Top">
              <variable id="v1" name="top"/>
              <informationRequirement id="q1"><requiredDecision href="#A"/></informationRequirement>
              <informationRequirement id="q2"><requiredDecision href="#B"/></informationRequirement>
              <literalExpression id="le"><text>score</text></literalExpression>
            </decision>
            """;

    assertThat(taskMessages(drd).get("le"))
        .singleElement()
        .asString()
        .contains("which several required decisions output: 'A', 'B'");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Base",
        "Base + 1",
        "Base.limit",
        "\"limit\"",
        "\"say \\\"limit\\\"\"",
        "limitValue",
        "x.limit",
        "limit(1)",
        "{limit: 1}.limit"
      })
  void shouldNotReportReferencesThatResolveOrAreNotVariables(String expression) {
    String drd =
        provider("Base", "limit")
            + """
            <decision id="Top" name="Top">
              <variable id="v1" name="top"/>
              <informationRequirement id="q1"><requiredDecision href="#Base"/></informationRequirement>
              <literalExpression id="le"><text>%s</text></literalExpression>
            </decision>
            """
                .formatted(expression.replace("\"", "&quot;"));

    assertThat(taskMessages(drd)).doesNotContainKey("le");
  }

  @Test
  void shouldNotReportDecisionWithoutRequirements() {
    String drd =
        provider("Base", "limit")
            + """
            <decision id="Other" name="Other">
              <variable id="v1" name="other"/>
              <literalExpression id="le"><text>limit</text></literalExpression>
            </decision>
            """;

    assertThat(taskMessages(drd)).doesNotContainKey("le");
  }

  @Test
  void shouldTolerateCyclicAndDanglingRequirements() {
    String drd =
        """
        <decision id="A" name="A">
          <informationRequirement id="q1"><requiredDecision href="#B"/></informationRequirement>
          <informationRequirement id="q2"><requiredDecision href="#Missing"/></informationRequirement>
          <decisionTable id="t1">
            <input id="i1"><inputExpression id="e1"><text>b</text></inputExpression></input>
            <output id="o1" name="a"/>
          </decisionTable>
        </decision>
        <decision id="B" name="B">
          <informationRequirement id="q3"><requiredDecision href="#A"/></informationRequirement>
          <decisionTable id="t2">
            <input id="i2"><inputExpression id="e2"><text>a</text></inputExpression></input>
            <output id="o2" name="b"/>
          </decisionTable>
        </decision>
        """;

    assertThat(taskMessages(drd)).containsOnlyKeys("e1", "e2");
  }

  private static String provider(String id, String outputName) {
    return """
        <decision id="%1$s" name="%1$s">
          <decisionTable id="t_%1$s">
            <input id="i_%1$s"><inputExpression id="e_%1$s"><text>in</text></inputExpression></input>
            <output id="o_%1$s" name="%2$s" typeRef="string"/>
          </decisionTable>
        </decision>
        """
        .formatted(id, outputName);
  }

  private static DiagramCheckResult check(String decisions) {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="drd" name="DRD" namespace="http://camunda.org/schema/1.0/dmn">
        %s
        </definitions>
        """
            .formatted(decisions);
    DmnModelInstance model =
        Dmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    return DiagramConverterFactory.getInstance()
        .get()
        .check("test.dmn", model, ConverterPropertiesFactory.getInstance().get());
  }

  /** Required-decision TASK messages, grouped by the element they are reported on. */
  private static Map<String, List<String>> taskMessages(String decisions) {
    return check(decisions).getResults().stream()
        .filter(
            r ->
                r.getMessages().stream()
                    .anyMatch(RequiredDecisionOutputReferenceVisitorTest::isRequiredDecisionTask))
        .collect(
            Collectors.toMap(
                DiagramCheckResult.ElementCheckResult::getElementId,
                r ->
                    r.getMessages().stream()
                        .filter(RequiredDecisionOutputReferenceVisitorTest::isRequiredDecisionTask)
                        .map(ElementCheckMessage::getMessage)
                        .toList()));
  }

  private static boolean isRequiredDecisionTask(ElementCheckMessage message) {
    return message.getSeverity() == Severity.TASK && LINK.equals(message.getLink());
  }
}
