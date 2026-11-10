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
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Node;

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
                + " decision 'Base', which this decision does not require directly. Camunda 7"
                + " evaluates all decisions of a requirement tree in one context, but Camunda 8"
                + " only exposes the results of direct requirements, and not by their output name,"
                + " so this reference evaluates to null. Add a requirement on 'Base' and use"
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
        .contains("does not require directly")
        .contains("Add a requirement on 'Base' and use 'baseRate' instead.");
  }

  /**
   * Same output name from several required decisions: Camunda 7 evaluates requirements in document
   * order and the later output overwrites the earlier one.
   */
  @Test
  void shouldSuggestLastEvaluatedOfSeveralProviders() {
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
        .contains("an output of the required decision 'B'")
        .contains("Use 'B' instead.");
  }

  /**
   * Camunda 7 evaluates every decision of the top-level decision's requirement tree in one context,
   * so a decision also sees outputs of decisions evaluated before it that it doesn't require at all
   * (siblings in the tree).
   */
  @Test
  void shouldReportReferenceToOutputOfSiblingDecision() {
    String drd =
        provider("A", "x")
            + """
            <decision id="B" name="B">
              <variable id="vb" name="B"/>
              <literalExpression id="le"><text>x + 1</text></literalExpression>
            </decision>
            <decision id="Top" name="Top">
              <variable id="vt" name="Top"/>
              <informationRequirement id="q1"><requiredDecision href="#A"/></informationRequirement>
              <informationRequirement id="q2"><requiredDecision href="#B"/></informationRequirement>
              <literalExpression id="lt"><text>B</text></literalExpression>
            </decision>
            """;

    assertThat(taskMessages(drd).get("le"))
        .singleElement()
        .asString()
        .contains("references 'x', an output of the decision 'A'")
        .contains("Add a requirement on 'A' and use 'A' instead.");
  }

  /** A sibling listed later is evaluated later, so its outputs aren't visible yet. */
  @Test
  void shouldNotReportReferenceToSiblingEvaluatedLater() {
    String drd =
        provider("A", "x")
            + """
            <decision id="B" name="B">
              <variable id="vb" name="B"/>
              <literalExpression id="le"><text>x + 1</text></literalExpression>
            </decision>
            <decision id="Top" name="Top">
              <variable id="vt" name="Top"/>
              <informationRequirement id="q1"><requiredDecision href="#B"/></informationRequirement>
              <informationRequirement id="q2"><requiredDecision href="#A"/></informationRequirement>
              <literalExpression id="lt"><text>B</text></literalExpression>
            </decision>
            """;

    assertThat(taskMessages(drd)).doesNotContainKey("le");
  }

  /**
   * The decision Camunda 7 evaluated last wins, even when it isn't among the referencing decision's
   * own requirements. Here 'Final' and 'Base' both output 'uid', and 'Sub' requires only 'Base'.
   */
  @ParameterizedTest
  @CsvSource({"Final,Sub,Final", "Sub,Final,Base"})
  void shouldSuggestDecisionThatCamunda7EvaluatedLast(
      String firstSibling, String secondSibling, String expectedProvider) {
    String drd =
        provider("Base", "uid")
            + """
            <decision id="Final" name="Final">
              <informationRequirement id="qf"><requiredDecision href="#Base"/></informationRequirement>
              <decisionTable id="tf">
                <input id="if"><inputExpression id="ef"><text>in</text></inputExpression></input>
                <output id="of" name="uid" typeRef="string"/>
              </decisionTable>
            </decision>
            <decision id="Sub" name="Sub">
              <variable id="vs" name="Sub"/>
              <informationRequirement id="qs"><requiredDecision href="#Base"/></informationRequirement>
              <literalExpression id="le"><text>uid</text></literalExpression>
            </decision>
            <decision id="Top" name="Top">
              <variable id="vt" name="Top"/>
              <informationRequirement id="q1"><requiredDecision href="#%s"/></informationRequirement>
              <informationRequirement id="q2"><requiredDecision href="#%s"/></informationRequirement>
              <literalExpression id="lt"><text>Sub</text></literalExpression>
            </decision>
            """
                .formatted(firstSibling, secondSibling);

    assertThat(taskMessages(drd).get("le"))
        .singleElement()
        .asString()
        .contains("decision '" + expectedProvider + "'")
        .containsPattern("[Uu]se '" + expectedProvider + "' instead\\.");
  }

  /** A decision shared by two top-level decisions can see different providers in each tree. */
  @Test
  void shouldReportAmbiguityAcrossTopLevelDecisions() {
    String drd =
        provider("A", "score")
            + provider("B", "score")
            + """
            <decision id="Shared" name="Shared">
              <variable id="vs" name="Shared"/>
              <literalExpression id="le"><text>score</text></literalExpression>
            </decision>
            <decision id="Top1" name="Top1">
              <variable id="v1" name="Top1"/>
              <informationRequirement id="q1"><requiredDecision href="#A"/></informationRequirement>
              <informationRequirement id="q2"><requiredDecision href="#Shared"/></informationRequirement>
              <literalExpression id="l1"><text>Shared</text></literalExpression>
            </decision>
            <decision id="Top2" name="Top2">
              <variable id="v2" name="Top2"/>
              <informationRequirement id="q3"><requiredDecision href="#B"/></informationRequirement>
              <informationRequirement id="q4"><requiredDecision href="#Shared"/></informationRequirement>
              <literalExpression id="l2"><text>Shared</text></literalExpression>
            </decision>
            """;

    assertThat(taskMessages(drd).get("le"))
        .singleElement()
        .asString()
        .contains("'A' (when evaluating 'Top1')")
        .contains("'B' (when evaluating 'Top2')");
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
        "x . limit",
        "x .\n limit",
        "x\n.limit",
        "limit(1)",
        "{limit: 1}.limit",
        // JUEL string literal, converted to the FEEL string "limit"
        "${'limit'}"
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[0..limit]",
        "[limit..10]",
        "[0 .. limit]",
        "x + limit",
        // JUEL variable access, converted to the bare FEEL name limit
        "${execution.getVariable(\"limit\")}"
      })
  void shouldReportReferenceAfterConversionToFeel(String expression) {
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

    assertThat(taskMessages(drd).get("le"))
        .singleElement()
        .asString()
        .contains("references 'limit'")
        .contains("Use 'Base' instead.");
  }

  /** Camunda 8 binds a decision table's result to its variable name, if it has one. */
  @ParameterizedTest
  @CsvSource({"rate, baseResult", "'rate,rateId', baseResult.rate"})
  void shouldSuggestVariableNameOfDecisionTable(String outputs, String expectedReference) {
    assertThat(taskMessages(tableAndTop("baseResult", outputs, "rate")).get("le"))
        .singleElement()
        .asString()
        .contains("Use '" + expectedReference + "' instead.");
  }

  /**
   * A name that Camunda 8 binds is only correct if it binds the value Camunda 7 read: a
   * single-output table named like its output is fine, a multi-output table isn't (Camunda 8 binds
   * the whole result context).
   */
  @ParameterizedTest
  @CsvSource({"Base, ''", "'Base,other', Base.Base"})
  void shouldReportNameBoundToDifferentValueInCamunda8(String outputs, String expectedReference) {
    var messages = taskMessages(tableAndTop(null, outputs, "Base + 1"));

    if (expectedReference.isEmpty()) {
      assertThat(messages).doesNotContainKey("le");
    } else {
      assertThat(messages.get("le"))
          .singleElement()
          .asString()
          .contains("Use '" + expectedReference + "' instead.");
    }
  }

  /** In Camunda 8, 'score' is the result of 'Other'; Camunda 7 read the output of 'Base'. */
  @Test
  void shouldReportNameBoundToAnotherDecisionInCamunda8() {
    String drd =
        provider("Base", "score")
            + """
            <decision id="Other" name="Other">
              <variable id="vo" name="score"/>
              <decisionTable id="t_o">
                <input id="i_o"><inputExpression id="e_o"><text>in</text></inputExpression></input>
                <output id="o_o" name="x" typeRef="string"/>
              </decisionTable>
            </decision>
            <decision id="Top" name="Top">
              <variable id="v1" name="top"/>
              <informationRequirement id="q1"><requiredDecision href="#Base"/></informationRequirement>
              <informationRequirement id="q2"><requiredDecision href="#Other"/></informationRequirement>
              <literalExpression id="le"><text>score</text></literalExpression>
            </decision>
            """;

    assertThat(taskMessages(drd).get("le"))
        .singleElement()
        .asString()
        .contains("an output of the required decision 'Base'")
        .contains("Use 'Base' instead.");
  }

  /** The check must not keep the checked document reachable, e.g. from a thread-local cache. */
  @Test
  void shouldNotRetainCheckedModel() throws InterruptedException {
    WeakReference<Node> document = checkAndForget(ISSUE_DRD);

    for (int i = 0; i < 50 && document.get() != null; i++) {
      System.gc();
      Thread.sleep(20);
    }
    assertThat(document.get()).isNull();
  }

  private static WeakReference<Node> checkAndForget(String decisions) {
    DmnModelInstance model = model(decisions);
    DiagramConverterFactory.getInstance()
        .get()
        .check("test.dmn", model, ConverterPropertiesFactory.getInstance().get());
    return new WeakReference<>(model.getDocument().getDomSource().getNode());
  }

  private static String tableAndTop(String variable, String outputs, String expression) {
    StringBuilder outputElements = new StringBuilder();
    for (String output : outputs.split(",")) {
      outputElements.append(
          "<output id=\"o_%1$s\" name=\"%1$s\" typeRef=\"string\"/>".formatted(output));
    }
    return """
        <decision id="Base" name="Base">
          %s
          <decisionTable id="t_b">
            <input id="i_b"><inputExpression id="e_b"><text>in</text></inputExpression></input>
            %s
          </decisionTable>
        </decision>
        <decision id="Top" name="Top">
          <variable id="v1" name="top"/>
          <informationRequirement id="q1"><requiredDecision href="#Base"/></informationRequirement>
          <literalExpression id="le"><text>%s</text></literalExpression>
        </decision>
        """
        .formatted(
            variable == null ? "" : "<variable id=\"v_b\" name=\"%s\"/>".formatted(variable),
            outputElements,
            expression);
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

  /**
   * Camunda 7 rejects cyclic requirements, so there is no evaluation order to reproduce. The cycle
   * is broken at the first decision in document order: B is evaluated before A, so A sees 'b' but B
   * doesn't see 'a'. The dangling requirement is ignored.
   */
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

    assertThat(taskMessages(drd)).containsOnlyKeys("e1");
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
    return DiagramConverterFactory.getInstance()
        .get()
        .check("test.dmn", model(decisions), ConverterPropertiesFactory.getInstance().get());
  }

  private static DmnModelInstance model(String decisions) {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="drd" name="DRD" namespace="http://camunda.org/schema/1.0/dmn">
        %s
        </definitions>
        """
            .formatted(decisions);
    return Dmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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
