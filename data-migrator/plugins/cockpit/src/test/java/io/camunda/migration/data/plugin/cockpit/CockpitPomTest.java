/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.plugin.cockpit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CockpitPomTest {

  @Test
  public void shouldPublishFixedJacksonDatabindAsProvided() throws Exception {
    String flattenedPomPath = System.getProperty("cockpit.flattenedPom");
    assertThat(flattenedPomPath)
        .as("The flattened Cockpit POM path must be configured by Maven")
        .isNotBlank();

    Path flattenedPom = Path.of(flattenedPomPath);
    assertThat(flattenedPom).as("The flattened Cockpit POM must exist").exists();

    Document document = createDocumentBuilderFactory().newDocumentBuilder().parse(flattenedPom.toFile());
    NodeList dependencies = document.getElementsByTagNameNS("*", "dependency");
    Element camundaWebapp = null;
    List<Element> jacksonDependencies = new ArrayList<>();

    for (int i = 0; i < dependencies.getLength(); i++) {
      Element dependency = (Element) dependencies.item(i);
      if ("org.camunda.bpm.webapp".equals(childText(dependency, "groupId"))
          && "camunda-webapp".equals(childText(dependency, "artifactId"))) {
        camundaWebapp = dependency;
      } else if ("com.fasterxml.jackson.core".equals(childText(dependency, "groupId"))
          && "jackson-databind".equals(childText(dependency, "artifactId"))) {
        jacksonDependencies.add(dependency);
      }
    }

    assertThat(camundaWebapp)
        .as("The flattened Cockpit POM must declare camunda-webapp directly")
        .isNotNull();
    Element exclusions = childElement(camundaWebapp, "exclusions");
    assertThat(exclusions)
        .as("The flattened Cockpit POM must declare camunda-webapp exclusions")
        .isNotNull();
    boolean excludesJacksonDatabind = false;
    NodeList exclusionNodes = exclusions.getChildNodes();
    for (int i = 0; i < exclusionNodes.getLength(); i++) {
      Node node = exclusionNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE
          || !"exclusion".equals(node.getLocalName())) {
        continue;
      }

      Element exclusion = (Element) node;
      if ("com.fasterxml.jackson.core".equals(childText(exclusion, "groupId"))
          && "jackson-databind".equals(childText(exclusion, "artifactId"))) {
        excludesJacksonDatabind = true;
        break;
      }
    }
    assertThat(excludesJacksonDatabind)
        .as("Camunda webapp must exclude transitive Jackson databind")
        .isTrue();

    assertThat(jacksonDependencies)
        .as("The flattened Cockpit POM must publish one direct Jackson databind dependency")
        .hasSize(1);
    Element jacksonDependency = jacksonDependencies.get(0);
    assertThat(childText(jacksonDependency, "scope")).isEqualTo("provided");

    String[] versionParts = childText(jacksonDependency, "version").split("\\.");
    assertThat(versionParts).hasSizeGreaterThanOrEqualTo(3);
    int major = Integer.parseInt(versionParts[0]);
    int minor = Integer.parseInt(versionParts[1]);
    int patch = Integer.parseInt(versionParts[2]);
    boolean fixed = major > 2 || major == 2 && (minor > 22 || minor == 22 && patch >= 2);
    assertThat(fixed)
        .as("Published Jackson databind must be at least 2.22.2 but was %s", childText(jacksonDependency, "version"))
        .isTrue();
  }

  protected static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }

  protected static String childText(Element element, String name) {
    Element child = childElement(element, name);
    assertThat(child).as("Element must contain %s", name).isNotNull();
    return child.getTextContent().trim();
  }

  protected static Element childElement(Element element, String name) {
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getLocalName())) {
        return (Element) node;
      }
    }
    return null;
  }
}
