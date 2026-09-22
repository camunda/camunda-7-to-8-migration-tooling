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
  void detectsInvalidAuthenticationMethod() {
    rewriteRun(
        properties(
            """
            camunda.client.auth.method=bogus
            """,
            """
            ~~(Invalid Camunda client authentication method 'bogus'. Use 'none', 'basic', or 'oidc' for camunda.client.auth.method.)~~>camunda.client.auth.method=bogus
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsInvalidAuthenticationMethodPlaceholderDefault() {
    rewriteRun(
        properties(
            """
            camunda.client.auth.method=${CAMUNDA_AUTH_METHOD:bogus}
            """,
            """
            ~~(Invalid Camunda client authentication method '${CAMUNDA_AUTH_METHOD:bogus}'. Use 'none', 'basic', or 'oidc' for camunda.client.auth.method.)~~>camunda.client.auth.method=${CAMUNDA_AUTH_METHOD:bogus}
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
  void detectsInvalidLegacyMode() {
    rewriteRun(
        properties(
            """
            zeebe.client.connection-mode=simple
            """,
            """
            ~~(Invalid Camunda client mode 'simple'. Use 'self-managed' or 'saas' for camunda.client.mode. Deprecated Camunda client property 'zeebe.client.connection-mode'. Use 'camunda.client.mode'.)~~>zeebe.client.connection-mode=simple
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsInvalidLegacyModePlaceholderDefault() {
    rewriteRun(
        properties(
            """
            zeebe.client.connection-mode=${CAMUNDA_MODE:simple}
            """,
            """
            ~~(Invalid Camunda client mode '${CAMUNDA_MODE:simple}'. Use 'self-managed' or 'saas' for camunda.client.mode. Deprecated Camunda client property 'zeebe.client.connection-mode'. Use 'camunda.client.mode'.)~~>zeebe.client.connection-mode=${CAMUNDA_MODE:simple}
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
  void detectsScalarAuthenticationRoot() {
    rewriteRun(
        properties(
            """
            camunda.client.auth=basic
            """,
            """
            ~~(Unsupported Camunda client configuration shape for 'camunda.client.auth'. Use nested authentication properties.)~~>camunda.client.auth=basic
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsSequenceAuthenticationRoot() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                auth: [basic]
            """,
            """
            camunda:
              client:
                ~~(Unsupported Camunda client configuration shape for 'camunda.client.auth'. Use nested authentication properties.)~~>auth: [basic]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsUnknownNonScalarAuthenticationProperty() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                auth:
                  simple: {}
                  unknown: [value]
            """,
            """
            camunda:
              client:
                auth:
                  ~~(Unsupported Camunda client authentication property 'camunda.client.auth.simple'. Configure authentication directly under camunda.client.auth.)~~>simple: {}
                  ~~(Unsupported Camunda client authentication property 'camunda.client.auth.unknown'. Configure authentication directly under camunda.client.auth.)~~>unknown: [value]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void acceptsSequenceClientCollectionProperty() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    fetch-variables: [foo, bar]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void acceptsValidYamlWorkerOverrides() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  override:
                    invoice:
                      max-jobs-active: 8
                      fetch-variables: [status]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsInvalidDynamicWorkerOverrideProperties() {
    rewriteRun(
        properties(
            """
            camunda.client.worker.override.invoice.max-jobs-active=8
            camunda.client.worker.override.payment.max-jobs-active=bogus
            """,
            """
            camunda.client.worker.override.invoice.max-jobs-active=8
            ~~(Invalid Camunda client configuration value 'bogus' for 'camunda.client.worker.override.payment.max-jobs-active'. Review it against the target Camunda Spring Boot starter type.)~~>camunda.client.worker.override.payment.max-jobs-active=bogus
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsInvalidDynamicWorkerOverrideYaml() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  override:
                    invoice:
                      max-jobs-active: bogus
            """,
            """
            camunda:
              client:
                worker:
                  override:
                    invoice:
                      ~~(Invalid Camunda client configuration value 'bogus' for 'camunda.client.worker.override.invoice.max-jobs-active'. Review it against the target Camunda Spring Boot starter type.)~~>max-jobs-active: bogus
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsRelativeGrpcAndRestAddresses() {
    rewriteRun(
        properties(
            """
            camunda.client.grpc-address=localhost
            camunda.client.rest-address=/api
            """,
            """
            ~~(Invalid Camunda client configuration value 'localhost' for 'camunda.client.grpc-address'. Review it against the target Camunda Spring Boot starter type.)~~>camunda.client.grpc-address=localhost
            ~~(Invalid Camunda client configuration value '/api' for 'camunda.client.rest-address'. Review it against the target Camunda Spring Boot starter type.)~~>camunda.client.rest-address=/api
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsMappingClientCollectionElements() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    fetch-variables:
                      - foo: bar
            """,
            """
            camunda:
              client:
                worker:
                  defaults:
                    ~~(Unsupported Camunda client configuration shape for 'camunda.client.worker.defaults.fetch-variables'. Sequence elements must match the target collection element type.)~~>fetch-variables:
                      - foo: bar
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsNestedSequenceClientCollectionElements() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    fetch-variables: [[foo]]
            """,
            """
            camunda:
              client:
                worker:
                  defaults:
                    ~~(Unsupported Camunda client configuration shape for 'camunda.client.worker.defaults.fetch-variables'. Sequence elements must match the target collection element type.)~~>fetch-variables: [[foo]]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsSequenceScalarClientProperty() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    max-jobs-active: [8]
            """,
            """
            camunda:
              client:
                worker:
                  defaults:
                    ~~(Unsupported Camunda client configuration shape for 'camunda.client.worker.defaults.max-jobs-active'. Use a scalar value.)~~>max-jobs-active: [8]
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  @Test
  void detectsIndexedScalarAuthenticationProperty() {
    rewriteRun(
        properties(
            """
            camunda.client.auth.client-id[0]=example
            """,
            """
            ~~(Unsupported Camunda client configuration shape for 'camunda.client.auth.client-id[0]'. Use a scalar value.)~~>camunda.client.auth.client-id[0]=example
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsInvalidScalarClientBinding() {
    rewriteRun(
        properties(
            """
            camunda.client.worker.defaults.max-jobs-active=bogus
            """,
            """
            ~~(Invalid Camunda client configuration value 'bogus' for 'camunda.client.worker.defaults.max-jobs-active'. Review it against the target Camunda Spring Boot starter type.)~~>camunda.client.worker.defaults.max-jobs-active=bogus
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void detectsInvalidScalarClientPlaceholderDefault() {
    rewriteRun(
        properties(
            """
            camunda.client.worker.defaults.max-jobs-active=${CAMUNDA_MAX_JOBS_ACTIVE:bogus}
            """,
            """
            ~~(Invalid Camunda client configuration value '${CAMUNDA_MAX_JOBS_ACTIVE:bogus}' for 'camunda.client.worker.defaults.max-jobs-active'. Review it against the target Camunda Spring Boot starter type.)~~>camunda.client.worker.defaults.max-jobs-active=${CAMUNDA_MAX_JOBS_ACTIVE:bogus}
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void marksLegacyZeebeAliasSequenceAsDeprecated() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                zeebe:
                  defaults:
                    fetch-variables: [foo, bar]
            """,
            """
            camunda:
              client:
                zeebe:
                  defaults:
                    ~~(Deprecated Camunda client property 'camunda.client.zeebe.defaults.fetch-variables'. Use 'camunda.client.worker.defaults.fetch-variables'.)~~>fetch-variables: [foo, bar]
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

  @Test
  void rejectsUnsupportedAuthenticationMethodWithoutStartingSpring() {
    assertThatThrownBy(() -> bind(Map.of("camunda.client.auth.method", "bogus")))
        .isInstanceOf(BindException.class);
  }

  @Test
  void rejectsInvalidScalarClientBindingWithoutStartingSpring() {
    assertThatThrownBy(() -> bind(Map.of("camunda.client.worker.defaults.max-jobs-active", "bogus")))
        .isInstanceOf(BindException.class);
  }

  private static CamundaClientProperties bind(Map<String, Object> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bind("camunda.client", Bindable.of(CamundaClientProperties.class))
        .get();
  }
}
