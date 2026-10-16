/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture;

import io.camunda.client.annotation.Deployment;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentResourcesTest {

  private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
  private static final String DMN = "https://www.omg.org/spec/DMN/20191111/MODEL/";
  private static final String SPRING_RESOLVER =
      "org.springframework.core.io.support.PathMatchingResourcePatternResolver";
  private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

  @Test
  void applicationPatternsResolveTheConvertedProcessAndDecision() throws Exception {
    Deployment deployment = Application.class.getAnnotation(Deployment.class);
    assertThat(deployment).isNotNull();

    String entries = verifyResources(deployment.resources());
    System.out.println(
        "CAMUNDA_DEPLOYMENT_RESOURCES "
            + "{\"source\":\"src/main/java/com/camunda/fixture/Application.java\","
            + "\"resolver\":\""
            + SPRING_RESOLVER
            + "\",\"entries\":"
            + entries
            + "}");
  }

  @Test
  void commaJoinedPatternsMatchNothingUnderTheSpringResolver() throws Exception {
    String joined = "classpath*:/converted-c8-*.bpmn,classpath*:/converted-c8-*.dmn";
    assertThat(resolver.getResources(joined)).isEmpty();
    assertThat(resolver.getResources("classpath*:/converted-c8-*.bpmn")).hasSize(2);
    assertThat(resolver.getResources("classpath*:/converted-c8-*.dmn")).hasSize(1);
    assertThatThrownBy(() -> verifyResources(joined))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("comma");
  }

  @Test
  void missingOriginalAndMixedResourcesCannotPassTheDeploymentCheck() {
    assertThatThrownBy(() -> verifyResources("classpath*:/missing-converted-c8-*.bpmn"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("nonempty");
    assertThatThrownBy(() -> verifyResources("classpath*:/original-process.bpmn"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("converted-c8-");
    assertThatThrownBy(() -> verifyResources("classpath*:/converted-c8-*"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("one resource type");
  }

  private String verifyResources(String... patterns) throws Exception {
    List<String> models = new ArrayList<>();
    StringJoiner entries = new StringJoiner(",", "[", "]");
    for (String pattern : patterns) {
      assertThat(pattern).as("one pattern per entry; reject comma joins").doesNotContain(",");
      Resource[] resources = resolver.getResources(pattern);
      assertThat(resources).as("nonempty Spring matches for %s", pattern).isNotEmpty();
      Set<String> types = new HashSet<>();
      StringJoiner matches = new StringJoiner(",", "[", "]");
      for (Resource resource : resources) {
        String name = resource.getFilename();
        assertThat(name).startsWith("converted-c8-");
        assertThat(name)
            .isIn(
                "converted-c8-process.bpmn",
                "converted-c8-second-process.bpmn",
                "converted-c8-decision.dmn");
        byte[] bytes;
        try (var input = resource.getInputStream()) {
          bytes = input.readAllBytes();
        }
        checkModelDocument(name, bytes);
        models.add(name);
        types.add(name.substring(name.lastIndexOf('.') + 1));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        matches.add(
            "{\"model\":"
                + json("src/test/resources/" + name)
                + ",\"sha256\":"
                + json(hash)
                + "}");
      }
      assertThat(types).as("one resource type per entry").hasSize(1);
      entries.add("{\"pattern\":" + json(pattern) + ",\"matches\":" + matches + "}");
    }
    assertThat(models)
        .containsExactlyInAnyOrder(
            "converted-c8-process.bpmn",
            "converted-c8-second-process.bpmn",
            "converted-c8-decision.dmn");
    return entries.toString();
  }

  private void checkModelDocument(String name, byte[] bytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    String namespace = name.endsWith(".bpmn") ? BPMN : DMN;
    assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo(namespace);
    assertThat(document.getDocumentElement().getLocalName()).isEqualTo("definitions");
    String element = name.endsWith(".bpmn") ? "process" : "decision";
    String id =
        name.endsWith(".dmn")
            ? "fixture-decision"
            : name.equals("converted-c8-second-process.bpmn")
                ? "fixture-second-process"
                : "fixture-process";
    var definitions = document.getElementsByTagNameNS(namespace, element);
    assertThat(definitions.getLength()).isEqualTo(1);
    assertThat(definitions.item(0).getAttributes().getNamedItem("id").getNodeValue())
        .isEqualTo(id);
  }

  private String json(String value) {
    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + escaped + "\"";
  }
}
