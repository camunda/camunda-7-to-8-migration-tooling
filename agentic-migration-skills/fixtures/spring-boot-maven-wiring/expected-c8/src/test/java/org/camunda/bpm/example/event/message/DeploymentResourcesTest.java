/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.event.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.annotation.Deployment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class DeploymentResourcesTest {
  private static final Set<String> DEPLOYMENT_INVENTORY =
      Set.of("converted-c8-message-start.bpmn", "converted-c8-message-decision.dmn");

  @Test
  void deploymentPatternsResolveEveryPackagedModel() throws IOException {
    Deployment deployment = MessageStartApplication.class.getAnnotation(Deployment.class);
    assertTrue(deployment != null, "The application must declare @Deployment");

    String[] patterns = deployment.resources();
    Set<String> includedTypes =
        DEPLOYMENT_INVENTORY.stream()
            .map(DeploymentResourcesTest::resourceType)
            .collect(Collectors.toSet());

    Set<String> resolvedResources = new HashSet<>();
    Set<String> resolvedTypes = new HashSet<>();
    PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver(MessageStartApplication.class.getClassLoader());
    String applicationRoot =
        MessageStartApplication.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toExternalForm();
    for (String pattern : patterns) {
      assertFalse(pattern.contains(","), "Do not combine patterns in one resource string");

      Resource[] resources = resolver.getResources(pattern);
      List<String> resourceLocations = new ArrayList<>(resources.length);
      for (Resource resource : resources) {
        resourceLocations.add(resource.getURL().toExternalForm());
      }
      Set<String> matches = applicationResourceNames(applicationRoot, resourceLocations);
      assertFalse(matches.isEmpty(), "Pattern must resolve packaged resources: " + pattern);
      assertEquals(matches.size(), resources.length, "Pattern must not resolve duplicate resources");
      assertTrue(
          DEPLOYMENT_INVENTORY.containsAll(matches),
          "Patterns must select only inventory resources");
      Set<String> resourceTypes =
          matches.stream().map(DeploymentResourcesTest::resourceType).collect(Collectors.toSet());
      assertEquals(1, resourceTypes.size(), "Each pattern must select one model type");
      assertTrue(
          includedTypes.containsAll(resourceTypes),
          "Patterns must select only included resource types");
      resolvedTypes.addAll(resourceTypes);
      addMatches(resolvedResources, matches);
    }

    assertEquals(DEPLOYMENT_INVENTORY, resolvedResources);
    assertEquals(
        includedTypes, resolvedTypes, "Each included type must have at least one pattern");
  }

  @Test
  void rejectsAResourceMatchedByMultiplePatterns() {
    Set<String> resolvedResources = new HashSet<>();
    Set<String> matches = Set.of("converted-c8-message-start.bpmn");

    addMatches(resolvedResources, matches);
    assertThrows(
        AssertionFailedError.class,
        () -> addMatches(resolvedResources, matches));
  }

  @Test
  void rejectsDependencyResourcesWithAnInventoryBasename() {
    assertThrows(
        AssertionFailedError.class,
        () ->
            applicationResourceNames(
                "file:/application/classes/",
                List.of("jar:file:/dependency.jar!/converted-c8-message-start.bpmn")));
  }

  private static Set<String> applicationResourceNames(
      String applicationRoot, List<String> resourceLocations) {
    Set<String> names = new HashSet<>();
    for (String location : resourceLocations) {
      assertTrue(
          location.startsWith(applicationRoot),
          "Resource must resolve from the application resource root: " + location);
      String name = location.substring(applicationRoot.length());
      assertFalse(name.isBlank(), "Resource location must identify a file: " + location);
      assertTrue(names.add(name), "Application resource location must be unique: " + location);
    }
    return names;
  }

  private static void addMatches(Set<String> resolvedResources, Set<String> matches) {
    for (String match : matches) {
      assertTrue(
          resolvedResources.add(match),
          "A resource must match only one deployment pattern: " + match);
    }
  }

  private static String resourceType(String filename) {
    int extensionStart = filename.lastIndexOf('.');
    assertTrue(extensionStart >= 0, "Every deployment resource must have a file extension");
    return filename.substring(extensionStart);
  }
}
