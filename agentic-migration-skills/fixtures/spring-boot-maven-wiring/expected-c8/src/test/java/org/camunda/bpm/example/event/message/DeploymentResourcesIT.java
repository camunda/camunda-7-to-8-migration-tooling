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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.annotation.Deployment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class DeploymentResourcesIT {
  private static final Set<String> INVENTORY =
      Set.of("converted-c8-message-start.bpmn", "converted-c8-message-decision.dmn");

  @Test
  void deploymentPatternsSelectExactlyThePackagedModels() throws IOException {
    Deployment deployment = MessageStartApplication.class.getAnnotation(Deployment.class);
    assertNotNull(deployment);

    var resolver =
        new PathMatchingResourcePatternResolver(MessageStartApplication.class.getClassLoader());
    String applicationRoot =
        MessageStartApplication.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toExternalForm();
    Set<String> selected = new HashSet<>();

    try (var jar = new ZipFile(Path.of("target/message-start-1.0-SNAPSHOT.jar").toFile())) {
      for (String pattern : deployment.resources()) {
        assertFalse(pattern.contains(","), "Use a separate resource entry for each pattern");
        Resource[] resources = resolver.getResources(pattern);
        assertTrue(resources.length > 0, "Pattern matches no resources: " + pattern);

        Set<String> types = new HashSet<>();
        for (Resource resource : resources) {
          String location = resource.getURL().toExternalForm();
          assertTrue(location.startsWith(applicationRoot), "Not an application resource: " + location);
          String name = location.substring(applicationRoot.length());
          assertTrue(INVENTORY.contains(name), "Unexpected deployment resource: " + name);
          assertTrue(selected.add(name), "Resource matches multiple patterns: " + name);
          assertNotNull(jar.getEntry("BOOT-INF/classes/" + name), "Not packaged: " + name);
          types.add(name.substring(name.lastIndexOf('.')));
        }
        assertEquals(1, types.size(), "Each pattern must select one resource type");
      }
    }
    assertEquals(INVENTORY, selected);
  }
}
