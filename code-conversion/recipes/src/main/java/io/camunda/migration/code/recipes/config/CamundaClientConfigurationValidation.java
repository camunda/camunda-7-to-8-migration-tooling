/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.spring.properties.CamundaClientProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Validates Camunda client settings that are known to prevent Spring Boot configuration binding. */
final class CamundaClientConfigurationValidation {

  private static final String AUTH_PREFIX = "camunda.client.auth.";
  private static final String LEGACY_MAPPINGS_RESOURCE =
      "camunda-client-legacy-property-mappings.properties";
  private static final String METADATA_RESOURCE = "META-INF/spring-configuration-metadata.json";
  private static final String MODE = "camunda.client.mode";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Set<String> SUPPORTED_AUTH_PROPERTIES = supportedAuthProperties();
  private static final Map<String, String> LEGACY_PROPERTY_MAPPINGS = legacyPropertyMappings();

  private CamundaClientConfigurationValidation() {}

  static Optional<String> finding(String key, String value) {
    if (key.equals(MODE) && isUnsupportedMode(value)) {
      return Optional.of(
          "Invalid Camunda client mode '"
              + value
              + "'. Use 'self-managed' or 'saas' for "
              + MODE
              + ".");
    }
    String replacement = LEGACY_PROPERTY_MAPPINGS.get(key);
    if (replacement != null) {
      return Optional.of(
          "Deprecated Camunda client property '"
              + key
              + "'. Use '"
              + replacement
              + "'.");
    }
    if (key.startsWith(AUTH_PREFIX) && !SUPPORTED_AUTH_PROPERTIES.contains(key)) {
      return Optional.of(
          "Unsupported Camunda client authentication property '"
              + key
              + "'. Configure authentication directly under camunda.client.auth.");
    }
    return Optional.empty();
  }

  private static boolean isUnsupportedMode(String value) {
    if (value.contains("${")) {
      return false;
    }
    String normalized = normalize(value);
    return Arrays.stream(CamundaClientProperties.ClientMode.values())
        .map(mode -> normalize(mode.name()))
        .noneMatch(normalized::equals);
  }

  private static Map<String, String> legacyPropertyMappings() {
    Properties mappings = new Properties();
    try (JarFile jarFile = targetStarterJar();
        InputStream stream = resource(jarFile, LEGACY_MAPPINGS_RESOURCE)) {
      mappings.load(stream);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Cannot load Camunda client legacy property mappings from the target starter.", e);
    }

    Map<String, String> legacyProperties = new HashMap<>();
    mappings.forEach(
        (propertyName, legacyPropertyNames) -> {
          for (String legacyPropertyName : legacyPropertyNames.toString().split(",")) {
            legacyProperties.put(legacyPropertyName.trim(), propertyName.toString());
          }
        });
    return Map.copyOf(legacyProperties);
  }

  private static InputStream resource(JarFile jarFile, String resourceName) throws IOException {
    JarEntry entry = jarFile.getJarEntry(resourceName);
    if (entry == null) {
      throw new IllegalStateException(
          "Cannot load " + resourceName + " from the target Camunda Spring Boot starter.");
    }
    return jarFile.getInputStream(entry);
  }

  private static Set<String> supportedAuthProperties() {
    try (JarFile jarFile = targetStarterJar();
        InputStream stream = resource(jarFile, METADATA_RESOURCE)) {
      JsonNode properties = OBJECT_MAPPER.readTree(stream).path("properties");
      Set<String> authProperties = new HashSet<>();
      for (JsonNode property : properties) {
        String name = property.path("name").asText();
        if (name.startsWith(AUTH_PREFIX)) {
          authProperties.add(name);
        }
      }
      return Set.copyOf(authProperties);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Cannot load Camunda client configuration metadata from the target starter.", e);
    }
  }

  private static JarFile targetStarterJar() {
    try {
      return new JarFile(
          Paths.get(
                  CamundaClientProperties.class
                      .getProtectionDomain()
                      .getCodeSource()
                      .getLocation()
                      .toURI())
              .toFile());
    } catch (IOException | URISyntaxException e) {
      throw new IllegalStateException(
          "Cannot locate the target Camunda Spring Boot starter for configuration validation.", e);
    }
  }

  private static String normalize(String value) {
    return value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
  }
}
