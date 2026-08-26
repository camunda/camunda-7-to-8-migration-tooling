/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.plugin.cockpit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PublishedPomTest {

  private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";
  private static final String FIXED_JACKSON_DATABIND_VERSION = "2.22.2";

  @Test
  public void publishesFixedJacksonDatabindAsProvidedDependency()
      throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    Document document =
        documentBuilderFactory.newDocumentBuilder().parse(Path.of(".flattened-pom.xml").toFile());

    Element dependencies = childElement(document.getDocumentElement(), "dependencies");
    Element jacksonDatabind =
        dependency(dependencies, "com.fasterxml.jackson.core", "jackson-databind");

    assertThat(jacksonDatabind).isNotNull();
    assertThat(childText(jacksonDatabind, "version"))
        .isEqualTo(FIXED_JACKSON_DATABIND_VERSION);
    assertThat(childText(jacksonDatabind, "scope")).isEqualTo("provided");
  }

  private static Element dependency(Element dependencies, String groupId, String artifactId) {
    if (dependencies == null) {
      return null;
    }

    NodeList dependencyNodes = dependencies.getChildNodes();
    for (int i = 0; i < dependencyNodes.getLength(); i++) {
      Node node = dependencyNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE
          || !"dependency".equals(node.getLocalName())) {
        continue;
      }

      Element dependency = (Element) node;
      if (groupId.equals(childText(dependency, "groupId"))
          && artifactId.equals(childText(dependency, "artifactId"))) {
        return dependency;
      }
    }
    return null;
  }

  private static Element childElement(Element parent, String name) {
    if (parent == null) {
      return null;
    }

    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE
          && name.equals(node.getLocalName())
          && MAVEN_POM_NAMESPACE.equals(node.getNamespaceURI())) {
        return (Element) node;
      }
    }
    return null;
  }

  private static String childText(Element parent, String name) {
    Element child = childElement(parent, name);
    return child == null ? null : child.getTextContent().trim();
  }
}
