/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

import io.camunda.client.spring.properties.CamundaClientProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ValidateCamundaClientConfigurationTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources(
        "io.camunda.migration.code.recipes.ValidateCamundaClientConfigurationRecipe");
  }

  @Test
  void detectsInvalidProperties() {
    rewriteRun(
        properties(
            """
            camunda.client.auth.simple.username=example
            camunda.client.auth.unsupported=example
            camunda.client.mode=simple
            """,
            """
            ~~(Unsupported Camunda client authentication property 'camunda.client.auth.simple.username'. Configure authentication directly under camunda.client.auth.)~~>camunda.client.auth.simple.username=example
            ~~(Unsupported Camunda client authentication property 'camunda.client.auth.unsupported'. Configure authentication directly under camunda.client.auth.)~~>camunda.client.auth.unsupported=example
            ~~(Invalid Camunda client mode 'simple'. Use 'self-managed' or 'saas' for camunda.client.mode.)~~>camunda.client.mode=simple
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsInvalidNestedYamlConfiguration() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                auth:
                  simple:
                    username: example
                mode: simple
            """,
            """
            camunda:
              client:
                auth:
                  simple:
                    ~~(Unsupported Camunda client authentication property 'camunda.client.auth.simple.username'. Configure authentication directly under camunda.client.auth.)~~>username: example
                ~~(Invalid Camunda client mode 'simple'. Use 'self-managed' or 'saas' for camunda.client.mode.)~~>mode: simple
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsInvalidDottedYamlConfiguration() {
    rewriteRun(
        yaml(
            """
            camunda.client.auth.simple.username: example
            camunda.client.mode: simple
            """,
            """
            ~~(Unsupported Camunda client authentication property 'camunda.client.auth.simple.username'. Configure authentication directly under camunda.client.auth.)~~>camunda.client.auth.simple.username: example
            ~~(Invalid Camunda client mode 'simple'. Use 'self-managed' or 'saas' for camunda.client.mode.)~~>camunda.client.mode: simple
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }

  @Test
  void acceptsUnauthenticatedSelfManagedConfiguration() {
    rewriteRun(
        properties(
            """
            camunda.client.grpc-address=http://localhost:26500
            camunda.client.mode=self-managed
            camunda.client.rest-address=http://localhost:8080
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void acceptsValidNestedYamlConfiguration() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                auth:
                  method: none
                grpc-address: http://localhost:26500
                mode: self-managed
                rest-address: http://localhost:8080
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void acceptsSupportedAuthenticationProperties() {
    rewriteRun(
        properties(
            """
            camunda.client.auth.client-id=example
            camunda.client.auth.client-secret=example
            camunda.client.auth.method=oidc
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void acceptsRelaxedAuthenticationPropertyNames() {
    rewriteRun(
        properties(
            """
            camunda.client.auth.clientId=example
            camunda.client.auth.client_id=example
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void marksLegacyZeebeAliasesAsDeprecated() {
    rewriteRun(
        properties(
            """
            camunda.client.zeebe.grpc-address=http://localhost:26500
            """,
            """
            ~~(Deprecated Camunda client property 'camunda.client.zeebe.grpc-address'. Use 'camunda.client.grpc-address'.)~~>camunda.client.zeebe.grpc-address=http://localhost:26500
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void ignoresNonApplicationFiles() {
    rewriteRun(
        properties(
            """
            camunda.client.mode=simple
            """,
            spec -> spec.path("src/main/resources/camunda.properties")));
  }

  @Test
  void detectsNonScalarModeConfiguration() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                mode: [simple]
            """,
            """
            camunda:
              client:
                ~~(Unsupported Camunda client configuration shape for 'camunda.client.mode'. Use a scalar value.)~~>mode: [simple]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void bindsValidSelfManagedConfigurationWithoutStartingSpring() {
    CamundaClientProperties properties =
        bind(
            Map.of(
                "camunda.client.grpc-address", "http://localhost:26500",
                "camunda.client.mode", "self-managed",
                "camunda.client.rest-address", "http://localhost:8080"));

    assertThat(properties.getMode()).isEqualTo(CamundaClientProperties.ClientMode.selfManaged);
  }

  @Test
  void rejectsUnsupportedModeWithoutStartingSpring() {
    assertThatThrownBy(() -> bind(Map.of("camunda.client.mode", "simple")))
        .isInstanceOf(BindException.class);
  }

  private static CamundaClientProperties bind(Map<String, Object> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bind("camunda.client", Bindable.of(CamundaClientProperties.class))
        .get();
  }
}
