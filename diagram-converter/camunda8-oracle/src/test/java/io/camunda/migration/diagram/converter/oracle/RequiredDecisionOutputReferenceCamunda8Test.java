/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.ConverterPropertiesFactory;
import io.camunda.migration.diagram.converter.DiagramCheckResult;
import io.camunda.migration.diagram.converter.DiagramConverter;
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.dmn.DmnEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Camunda 8 is the oracle for the required-decision output check: a converted fixture, with every
 * finding of the report applied as it's worded, must return in the Camunda 8 DMN engine what
 * Camunda 7 returns for the original. The Camunda 7 results are pinned in {@code
 * camunda7-results.txt}, which core checks against the Camunda 7 DMN engine.
 */
class RequiredDecisionOutputReferenceCamunda8Test {
  private static final String DMN = "https://www.omg.org/spec/DMN/20191111/MODEL/";
  private static final Map<String, Object> VARIABLES =
      Map.of("customerId", "C1", "auditId", "X", "customer", Map.of("limit", 3), "points", 4);

  private static final Pattern FINDING =
      Pattern.compile(
          "references '(?<name>[^']+)', an output of the (?:required )?decision '[^']+'.*?"
              + "(?:Add a requirement on '(?<require>[^']+)' and use|Use) '(?<use>[^']+)' instead\\.");
  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

  private static DmnEngine camunda8;

  @BeforeAll
  static void buildEngine() {
    camunda8 = new DmnEngine.Builder().build();
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvFileSource(
      resources = "/required-decision-outputs/camunda7-results.txt",
      delimiter = '|',
      ignoreLeadingAndTrailingWhitespace = true)
  void shouldReturnCamunda7ResultAfterApplyingFindings(
      String fixture, String decisionId, String camunda7Result, String marker) {
    Conversion conversion = convert(fixture);

    if ("ambiguous".equals(marker)) {
      assertThat(conversion.ambiguousFindings())
          .as("the report flags that no single rewrite fits")
          .anyMatch(m -> m.contains("(when evaluating '" + decisionId + "')"));
      return;
    }
    assertThat(evaluate(conversion.fixedXml(), decisionId))
        .as("converted, with the findings applied")
        .isEqualTo(camunda7Result);
    if ("unchanged".equals(marker)) {
      assertThat(evaluate(conversion.convertedXml(), decisionId))
          .as("converted, without the findings")
          .isEqualTo(camunda7Result);
    } else {
      assertThat(evaluate(conversion.convertedXml(), decisionId))
          .as("converted, without the findings")
          .isNotEqualTo(camunda7Result);
    }
  }

  /** The hand-written rewrite of c7-style.dmn must return the same values too. */
  @ParameterizedTest(name = "c8-style.dmn {1}")
  @CsvFileSource(
      resources = "/required-decision-outputs/camunda7-results.txt",
      delimiter = '|',
      ignoreLeadingAndTrailingWhitespace = true)
  void shouldReturnCamunda7ResultForHandWrittenRewrite(
      String fixture, String decisionId, String camunda7Result, String marker) {
    if (!"c7-style.dmn".equals(fixture) || "ambiguous".equals(marker)) {
      return;
    }
    Conversion conversion = convert("c8-style.dmn");

    assertThat(conversion.findings()).isEmpty();
    assertThat(evaluate(conversion.convertedXml(), decisionId)).isEqualTo(camunda7Result);
  }

  private record Conversion(
      String convertedXml,
      String fixedXml,
      Map<String, List<String>> findings,
      List<String> ambiguousFindings) {}

  private static Conversion convert(String fixture) {
    DiagramConverter converter = DiagramConverterFactory.getInstance().get();
    DmnModelInstance model = Dmn.readModelFromStream(resource(fixture));
    DiagramCheckResult result =
        converter.check(fixture, model, ConverterPropertiesFactory.getInstance().get());
    StringWriter converted = new StringWriter();
    converter.printXml(model.getDocument(), false, converted);

    Map<String, List<String>> findings = new LinkedHashMap<>();
    List<String> ambiguous = new ArrayList<>();
    result
        .getResults()
        .forEach(
            r ->
                r.getMessages().stream()
                    .map(DiagramCheckResult.ElementCheckMessage::getMessage)
                    .filter(m -> m.contains("references '"))
                    .forEach(
                        m -> {
                          if (FINDING.matcher(m).find()) {
                            findings
                                .computeIfAbsent(r.getElementId(), k -> new ArrayList<>())
                                .add(m);
                          } else {
                            ambiguous.add(m);
                          }
                        }));
    return new Conversion(
        converted.toString(), applyFindings(converted.toString(), findings), findings, ambiguous);
  }

  /**
   * Applies each finding as a user following its wording would: replaces the referenced name in the
   * expression of the reported element, outside string literals, and adds the requirement the
   * finding asks for.
   */
  private static String applyFindings(String xml, Map<String, List<String>> findings) {
    Document document = parse(xml);
    findings.forEach(
        (elementId, messages) -> {
          Element element = byId(document, elementId);
          assertThat(element).as("reported element %s", elementId).isNotNull();
          Element text = firstChild(element, "text");
          Map<String, String> replacements = new LinkedHashMap<>();
          for (String message : messages) {
            Matcher matcher = FINDING.matcher(message);
            assertThat(matcher.find()).isTrue();
            replacements.put(matcher.group("name"), matcher.group("use"));
            if (matcher.group("require") != null) {
              addRequirement(enclosingDecision(element), matcher.group("require"));
            }
          }
          text.setTextContent(replace(text.getTextContent(), replacements));
        });
    return print(document);
  }

  /**
   * Replaces whole-word occurrences of the names, and their backtick-quoted form, outside strings.
   */
  private static String replace(String expression, Map<String, String> replacements) {
    StringBuilder result = new StringBuilder();
    Matcher literal = STRING_LITERAL.matcher(expression);
    int last = 0;
    while (literal.find()) {
      result.append(replaceNames(expression.substring(last, literal.start()), replacements));
      result.append(literal.group());
      last = literal.end();
    }
    result.append(replaceNames(expression.substring(last), replacements));
    return result.toString();
  }

  private static String replaceNames(String code, Map<String, String> replacements) {
    String alternatives =
        replacements.keySet().stream()
            .sorted((a, b) -> b.length() - a.length())
            .map(name -> "`" + Pattern.quote(name) + "`|(?<!\\w)" + Pattern.quote(name) + "(?!\\w)")
            .reduce((a, b) -> a + "|" + b)
            .orElseThrow();
    Matcher matcher = Pattern.compile(alternatives).matcher(code);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group().replace("`", "");
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacements.get(name)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static void addRequirement(Element decision, String requiredId) {
    for (Element requirement : children(decision, "informationRequirement")) {
      Element required = firstChild(requirement, "requiredDecision");
      if (required != null && ("#" + requiredId).equals(required.getAttribute("href"))) {
        return;
      }
    }
    Document document = decision.getOwnerDocument();
    Element requirement = document.createElementNS(DMN, "informationRequirement");
    requirement.setAttribute("id", "Fix_" + decision.getAttribute("id") + "_" + requiredId);
    Element required = document.createElementNS(DMN, "requiredDecision");
    required.setAttribute("href", "#" + requiredId);
    requirement.appendChild(required);
    // requirements precede the decision logic
    Node logic =
        firstChild(decision, "decisionTable") != null
            ? firstChild(decision, "decisionTable")
            : firstChild(decision, "literalExpression");
    decision.insertBefore(requirement, logic);
  }

  private static String evaluate(String xml, String decisionId) {
    var result =
        camunda8.eval(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), decisionId, VARIABLES);
    if (result.isLeft()) {
      return "failure: " + result.left().get();
    }
    DmnEngine.EvalResult value = result.toOption().get();
    return value instanceof DmnEngine.Result r ? String.valueOf(r.value()) : "null";
  }

  private static Element enclosingDecision(Element element) {
    Node current = element;
    while (current != null && !"decision".equals(current.getLocalName())) {
      current = current.getParentNode();
    }
    return (Element) current;
  }

  private static Element byId(Document document, String id) {
    NodeList all = document.getElementsByTagNameNS("*", "*");
    for (int i = 0; i < all.getLength(); i++) {
      Element element = (Element) all.item(i);
      if (id.equals(element.getAttribute("id"))) {
        return element;
      }
    }
    return null;
  }

  private static List<Element> children(Element parent, String localName) {
    List<Element> result = new ArrayList<>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && localName.equals(element.getLocalName())) {
        result.add(element);
      }
    }
    return result;
  }

  private static Element firstChild(Element parent, String localName) {
    List<Element> children = children(parent, localName);
    return children.isEmpty() ? null : children.get(0);
  }

  private static Document parse(String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory
          .newDocumentBuilder()
          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String print(Document document) {
    try {
      StringWriter writer = new StringWriter();
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.transform(new DOMSource(document), new StreamResult(writer));
      return writer.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static InputStream resource(String fixture) {
    InputStream stream =
        RequiredDecisionOutputReferenceCamunda8Test.class.getResourceAsStream(
            "/required-decision-outputs/" + fixture);
    if (stream == null) {
      throw new UncheckedIOException(new IOException("missing fixture " + fixture));
    }
    return stream;
  }
}
