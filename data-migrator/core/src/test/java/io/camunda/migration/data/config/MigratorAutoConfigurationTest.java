/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.C7Properties;
import io.camunda.migration.data.config.property.DataSourceProperties;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.DataSourceRegistry;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MigratorAutoConfigurationTest {

  @Test
  public void shouldPreferMigratorCommonsMapperWhenCamundaEngineIsFirstOnClasspath() throws IOException {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader parentClassLoader = originalClassLoader != null ? originalClassLoader : getClass().getClassLoader();
    URL engineCommonsResource = Collections.list(parentClassLoader.getResources(
            MigratorAutoConfiguration.CAMUNDA_COMMONS_RESOURCE))
        .stream()
        .filter(resource -> resource.toExternalForm().contains("camunda-engine"))
        .findFirst()
        .orElseThrow();

    ClassLoader engineFirstClassLoader = new ClassLoader(parentClassLoader) {
      @Override
      public URL getResource(String name) {
        if (MigratorAutoConfiguration.CAMUNDA_COMMONS_RESOURCE.equals(name)) {
          return engineCommonsResource;
        }
        return super.getResource(name);
      }

      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        if (MigratorAutoConfiguration.CAMUNDA_COMMONS_RESOURCE.equals(name)) {
          return Collections.enumeration(List.of(engineCommonsResource));
        }
        return super.getResources(name);
      }
    };

    var properties = new MigratorProperties();
    var c7Properties = new C7Properties();
    c7Properties.setDataSource(new DataSourceProperties());
    properties.setC7(c7Properties);
    var registry = new DataSourceRegistry(properties);
    try {
      Thread.currentThread().setContextClassLoader(engineFirstClassLoader);
      var processEngineConfiguration =
          new MigratorAutoConfiguration(properties).processEngineConfiguration(registry);
      var patchedCommonsResource =
          MigratorAutoConfiguration.class.getClassLoader().getResource(MigratorAutoConfiguration.MIGRATOR_COMMONS_RESOURCE);

      assertThat(engineFirstClassLoader.getResource(MigratorAutoConfiguration.CAMUNDA_COMMONS_RESOURCE))
          .isEqualTo(engineCommonsResource);
      assertThat(patchedCommonsResource).isNotNull();
      assertThat(processEngineConfiguration.getClassLoader()
              .getResource(MigratorAutoConfiguration.CAMUNDA_COMMONS_RESOURCE))
          .isEqualTo(patchedCommonsResource);
      assertThat(Collections.list(processEngineConfiguration.getClassLoader()
              .getResources(MigratorAutoConfiguration.CAMUNDA_COMMONS_RESOURCE)))
          .containsExactly(patchedCommonsResource);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
      registry.close();
    }
  }
}
