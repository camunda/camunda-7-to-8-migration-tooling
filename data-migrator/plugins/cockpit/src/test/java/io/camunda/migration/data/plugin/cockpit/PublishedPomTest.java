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
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;

public class PublishedPomTest {

  @Test
  public void shouldPublishFixedJacksonDatabindAsProvidedDependency() throws Exception {
    var flattenedPom =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(Path.of(System.getProperty("basedir"), ".flattened-pom.xml").toFile());
    var dependencies = flattenedPom.getElementsByTagName("dependency");
    Element camundaWebapp = null;
    Element jacksonDatabind = null;

    for (int i = 0; i < dependencies.getLength(); i++) {
      var dependency = (Element) dependencies.item(i);
      if ("org.camunda.bpm.webapp".equals(childText(dependency, "groupId"))
          && "camunda-webapp".equals(childText(dependency, "artifactId"))) {
        camundaWebapp = dependency;
      } else if ("com.fasterxml.jackson.core".equals(childText(dependency, "groupId"))
          && "jackson-databind".equals(childText(dependency, "artifactId"))) {
        jacksonDatabind = dependency;
      }
    }

    assertThat(camundaWebapp)
        .as("the flattened Cockpit POM must declare camunda-webapp directly")
        .isNotNull();
    var exclusions = camundaWebapp.getElementsByTagName("exclusion");
    var excludesJacksonDatabind = false;
    for (int i = 0; i < exclusions.getLength(); i++) {
      var exclusion = (Element) exclusions.item(i);
      if ("com.fasterxml.jackson.core".equals(childText(exclusion, "groupId"))
          && "jackson-databind".equals(childText(exclusion, "artifactId"))) {
        excludesJacksonDatabind = true;
        break;
      }
    }
    assertThat(excludesJacksonDatabind)
        .as("camunda-webapp must exclude transitive Jackson databind")
        .isTrue();

    assertThat(jacksonDatabind)
        .as("the flattened Cockpit POM must declare Jackson databind directly")
        .isNotNull();
    assertThat(childText(jacksonDatabind, "scope")).isEqualTo("provided");

    var version =
        Arrays.stream(childText(jacksonDatabind, "version").split("\\."))
            .mapToInt(Integer::parseInt)
            .toArray();
    assertThat(version).hasSizeGreaterThanOrEqualTo(3);
    assertThat(Arrays.compare(version, new int[] {2, 21, 5}))
        .as("the flattened Cockpit POM must select a fixed Jackson databind version")
        .isGreaterThanOrEqualTo(0);
  }

  protected static String childText(Element parent, String name) {
    var children = parent.getChildNodes();
    Element child = null;
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element && name.equals(element.getTagName())) {
        child = element;
        break;
      }
    }
    assertThat(child).as("Element must contain direct child %s", name).isNotNull();
    return child.getTextContent().trim();
  }
}
