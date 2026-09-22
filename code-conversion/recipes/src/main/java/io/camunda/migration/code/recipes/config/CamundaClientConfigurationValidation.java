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
import io.camunda.client.spring.properties.CamundaClientAuthProperties;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

/**
 * Validates Camunda client settings against the starter metadata bundled with the selected recipe
 * version.
 */
final class CamundaClientConfigurationValidation {

  private static final ConfigurationPropertyName AUTH_PREFIX =
      propertyName("camunda.client.auth");
  private static final ConfigurationPropertyName AUTH_METHOD =
      propertyName("camunda.client.auth.method");
  private static final String LEGACY_MAPPINGS_RESOURCE =
      "camunda-client-legacy-property-mappings.properties";
  private static final String METADATA_RESOURCE = "META-INF/spring-configuration-metadata.json";
  private static final ConfigurationPropertyName MODE = propertyName("camunda.client.mode");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern INDEXED_PROPERTY_PATTERN = Pattern.compile("\\[[^\\]]+\\]");
  private static final AuthPropertyMetadata AUTH_PROPERTY_METADATA = authPropertyMetadata();
  private static final Set<ConfigurationPropertyName> SUPPORTED_AUTH_PROPERTIES =
      AUTH_PROPERTY_METADATA.supportedProperties();
  private static final Set<ConfigurationPropertyName> COLLECTION_AUTH_PROPERTIES =
      AUTH_PROPERTY_METADATA.collectionProperties();
  private static final Map<ConfigurationPropertyName, String> LEGACY_PROPERTY_MAPPINGS =
      legacyPropertyMappings();

  private CamundaClientConfigurationValidation() {}

  static Optional<String> finding(String key, String value) {
    PropertyReference propertyReference = propertyReference(key);
    ConfigurationPropertyName propertyName = propertyReference.propertyName();
    String replacement = LEGACY_PROPERTY_MAPPINGS.get(propertyName);
    ConfigurationPropertyName effectivePropertyName =
        replacement == null ? propertyName : propertyName(replacement);
    return withDeprecation(
        semanticFinding(key, effectivePropertyName, value, propertyReference.isIndexed()),
        key,
        replacement);
  }

  static Optional<String> shapeFinding(String key, ValueShape valueShape) {
    PropertyReference propertyReference = propertyReference(key);
    ConfigurationPropertyName propertyName = propertyReference.propertyName();
    String replacement = LEGACY_PROPERTY_MAPPINGS.get(propertyName);
    ConfigurationPropertyName effectivePropertyName =
        replacement == null ? propertyName : propertyName(replacement);
    return withDeprecation(
        shapeFinding(key, effectivePropertyName, valueShape), key, replacement);
  }

  private static Optional<String> semanticFinding(
      String key, ConfigurationPropertyName propertyName, String value, boolean indexed) {
    if (propertyName.equals(MODE)) {
      if (indexed) {
        return Optional.of(
            "Unsupported Camunda client configuration shape for '"
                + key
                + "'. Use a scalar value.");
      }
      if (isUnsupportedMode(value)) {
        return Optional.of(
            "Invalid Camunda client mode '"
                + value
                + "'. Use 'self-managed' or 'saas' for "
                + MODE.toString()
                + ".");
      }
    }
    if (propertyName.equals(AUTH_PREFIX)) {
      return Optional.of(
          "Unsupported Camunda client configuration shape for '"
              + key
              + "'. Use nested authentication properties.");
    }
    if (propertyName.equals(AUTH_METHOD)) {
      if (indexed) {
        return Optional.of(
            "Unsupported Camunda client configuration shape for '"
                + key
                + "'. Use a scalar value.");
      }
      if (isUnsupportedAuthMethod(value)) {
        return Optional.of(
            "Invalid Camunda client authentication method '"
                + value
                + "'. Use 'none', 'basic', or 'oidc' for "
                + AUTH_METHOD
                + ".");
      }
    }
    if (AUTH_PREFIX.isAncestorOf(propertyName)
        && !SUPPORTED_AUTH_PROPERTIES.contains(propertyName)) {
      return Optional.of(
          "Unsupported Camunda client authentication property '"
              + key
              + "'. Configure authentication directly under camunda.client.auth.");
    }
    if (indexed
        && SUPPORTED_AUTH_PROPERTIES.contains(propertyName)
        && !COLLECTION_AUTH_PROPERTIES.contains(propertyName)) {
      return Optional.of(
          "Unsupported Camunda client configuration shape for '"
              + key
              + "'. Use a scalar value.");
    }
    return Optional.empty();
  }

  private static Optional<String> shapeFinding(
      String key, ConfigurationPropertyName propertyName, ValueShape valueShape) {
    if (propertyName.equals(AUTH_PREFIX)) {
      return Optional.of(
          "Unsupported Camunda client configuration shape for '"
              + key
              + "'. Use nested authentication properties.");
    }
    if (propertyName.equals(MODE)) {
      return Optional.of(
          "Unsupported Camunda client configuration shape for '"
              + key
              + "'. Use a scalar value.");
    }
    if (SUPPORTED_AUTH_PROPERTIES.contains(propertyName)) {
      if (valueShape == ValueShape.SEQUENCE
          && COLLECTION_AUTH_PROPERTIES.contains(propertyName)) {
        return Optional.empty();
      }
      return Optional.of(
          "Unsupported Camunda client configuration shape for '"
              + key
              + "'. Use a scalar value.");
    }
    if (AUTH_PREFIX.isAncestorOf(propertyName)) {
      return Optional.of(
          "Unsupported Camunda client authentication property '"
              + key
              + "'. Configure authentication directly under camunda.client.auth.");
    }
    return Optional.empty();
  }

  private static Optional<String> withDeprecation(
      Optional<String> finding, String key, String replacement) {
    if (replacement == null) {
      return finding;
    }
    String deprecation =
        "Deprecated Camunda client property '"
            + key
            + "'. Use '"
            + replacement
            + "'.";
    return Optional.of(finding.map(message -> message + " " + deprecation).orElse(deprecation));
  }

  static boolean isAuthenticationContainer(String key) {
    return isAuthenticationContainer(propertyName(key));
  }

  static boolean isUnknownAuthenticationDescendant(String key) {
    ConfigurationPropertyName propertyName = propertyReference(key).propertyName();
    return AUTH_PREFIX.isAncestorOf(propertyName)
        && !SUPPORTED_AUTH_PROPERTIES.contains(propertyName)
        && !isAuthenticationContainer(propertyName);
  }

  private static boolean isAuthenticationContainer(ConfigurationPropertyName propertyName) {
    return propertyName.equals(AUTH_PREFIX)
        || SUPPORTED_AUTH_PROPERTIES.stream().anyMatch(propertyName::isAncestorOf);
  }

  private static boolean isUnsupportedMode(String value) {
    return isUnsupportedEnumValue(
        value, Arrays.stream(CamundaClientProperties.ClientMode.values()).map(Enum::name));
  }

  private static boolean isUnsupportedAuthMethod(String value) {
    return isUnsupportedEnumValue(
        value, Arrays.stream(CamundaClientAuthProperties.AuthMethod.values()).map(Enum::name));
  }

  private static boolean isUnsupportedEnumValue(String value, Stream<String> supportedValues) {
    if (value.contains("${")) {
      return false;
    }
    String normalized = normalize(value);
    return supportedValues
        .map(CamundaClientConfigurationValidation::normalize)
        .noneMatch(normalized::equals);
  }

  private static Map<ConfigurationPropertyName, String> legacyPropertyMappings() {
    Properties mappings = new Properties();
    try (JarFile jarFile = targetStarterJar();
        InputStream stream = resource(jarFile, LEGACY_MAPPINGS_RESOURCE)) {
      mappings.load(stream);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Cannot load Camunda client legacy property mappings from the target starter.", e);
    }

    Map<ConfigurationPropertyName, String> legacyProperties = new HashMap<>();
    mappings.forEach(
        (currentPropertyName, legacyPropertyNames) -> {
          for (String legacyPropertyName : legacyPropertyNames.toString().split(",")) {
            legacyProperties.put(
                propertyName(legacyPropertyName.trim()), currentPropertyName.toString());
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

  private static AuthPropertyMetadata authPropertyMetadata() {
    try (JarFile jarFile = targetStarterJar();
        InputStream stream = resource(jarFile, METADATA_RESOURCE)) {
      JsonNode properties = OBJECT_MAPPER.readTree(stream).path("properties");
      Set<ConfigurationPropertyName> authProperties = new HashSet<>();
      Set<ConfigurationPropertyName> collectionAuthProperties = new HashSet<>();
      for (JsonNode property : properties) {
        String name = property.path("name").asText();
        ConfigurationPropertyName propertyName = propertyName(name);
        if (AUTH_PREFIX.isAncestorOf(propertyName)) {
          authProperties.add(propertyName);
          if (isCollectionType(property.path("type").asText(""))) {
            collectionAuthProperties.add(propertyName);
          }
        }
      }
      return new AuthPropertyMetadata(
          Set.copyOf(authProperties), Set.copyOf(collectionAuthProperties));
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

  private static ConfigurationPropertyName propertyName(String name) {
    return ConfigurationPropertyName.adapt(name, '.');
  }

  private static PropertyReference propertyReference(String key) {
    String normalizedKey = INDEXED_PROPERTY_PATTERN.matcher(key).replaceAll("");
    return new PropertyReference(propertyName(normalizedKey), !normalizedKey.equals(key));
  }

  private static boolean isCollectionType(String type) {
    return type.endsWith("[]")
        || type.startsWith("java.lang.Iterable")
        || type.startsWith("java.util.Collection")
        || type.startsWith("java.util.List")
        || type.startsWith("java.util.Set");
  }

  private static String normalize(String value) {
    return value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
  }

  private record AuthPropertyMetadata(
      Set<ConfigurationPropertyName> supportedProperties,
      Set<ConfigurationPropertyName> collectionProperties) {}

  private record PropertyReference(ConfigurationPropertyName propertyName, boolean isIndexed) {}

  enum ValueShape {
    MAPPING,
    SEQUENCE
  }
}
