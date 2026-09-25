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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.annotation.Deployment;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
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
    assertEquals(2, patterns.length, "Use one resource pattern for each model type");

    Set<String> resolvedResources = new HashSet<>();
    PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver(MessageStartApplication.class.getClassLoader());
    for (String pattern : patterns) {
      assertFalse(pattern.contains(","), "Do not combine patterns in one resource string");
      String extension =
          pattern.endsWith("*.bpmn") ? ".bpmn" : pattern.endsWith("*.dmn") ? ".dmn" : "";
      assertFalse(extension.isEmpty(), "Each pattern must select one model type");

      Resource[] resources = resolver.getResources(pattern);
      Set<String> matches =
          Arrays.stream(resources).map(Resource::getFilename).collect(Collectors.toSet());
      assertFalse(matches.isEmpty(), "Pattern must resolve packaged resources: " + pattern);
      assertEquals(matches.size(), resources.length, "Pattern must not resolve duplicate resources");
      assertTrue(DEPLOYMENT_INVENTORY.containsAll(matches));
      assertTrue(matches.stream().allMatch(name -> name.endsWith(extension)));
      resolvedResources.addAll(matches);
    }

    assertEquals(DEPLOYMENT_INVENTORY, resolvedResources);
  }
}
