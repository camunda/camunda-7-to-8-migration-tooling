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
import java.lang.reflect.Array;
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
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/**
 * Validates Camunda client settings against the starter metadata bundled with the selected recipe
 * version.
 */
final class CamundaClientConfigurationValidation {

  private static final ConfigurationPropertyName CLIENT_PREFIX = propertyName("camunda.client");
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
  private static final ClientPropertyMetadata CLIENT_PROPERTY_METADATA = clientPropertyMetadata();
  private static final Set<ConfigurationPropertyName> SUPPORTED_AUTH_PROPERTIES =
      CLIENT_PROPERTY_METADATA.authProperties();
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

  static String effectivePropertyName(String key) {
    PropertyReference propertyReference = propertyReference(key);
    String replacement = LEGACY_PROPERTY_MAPPINGS.get(propertyReference.propertyName());
    return replacement == null
        ? propertyReference.propertyName().toString()
        : propertyName(replacement).toString();
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
    if (propertyName.equals(AUTH_PREFIX)) {
      return Optional.of(
          "Unsupported Camunda client configuration shape for '"
              + key
              + "'. Use nested authentication properties.");
    }
    if (AUTH_PREFIX.isAncestorOf(propertyName)
        && !SUPPORTED_AUTH_PROPERTIES.contains(propertyName)) {
      return Optional.of(
          "Unsupported Camunda client authentication property '"
              + key
              + "'. Configure authentication directly under camunda.client.auth.");
    }
    Optional<PropertyMetadata> propertyMetadata = CLIENT_PROPERTY_METADATA.property(propertyName);
    if (propertyMetadata.isEmpty()) {
      return Optional.empty();
    }
    if (indexed && !propertyMetadata.get().collection()) {
      return Optional.of(unsupportedScalarShapeFinding(key));
    }
    Optional<String> candidate = bindingCandidate(value);
    if (candidate.isEmpty()) {
      return enumFinding(propertyName, value);
    }
    return !isBindable(propertyMetadata.get(), candidate.get(), indexed)
        ? Optional.of(invalidValueFinding(key, propertyName, value))
        : Optional.empty();
  }

  static Optional<String> sequenceFinding(String key, Iterable<String> values) {
    PropertyReference propertyReference = propertyReference(key);
    ConfigurationPropertyName propertyName = propertyReference.propertyName();
    String replacement = LEGACY_PROPERTY_MAPPINGS.get(propertyName);
    ConfigurationPropertyName effectivePropertyName =
        replacement == null ? propertyName : propertyName(replacement);
    Optional<PropertyMetadata> propertyMetadata =
        CLIENT_PROPERTY_METADATA.property(effectivePropertyName);
    if (propertyMetadata.isEmpty() || !propertyMetadata.get().collection()) {
      return withDeprecation(
          shapeFinding(key, effectivePropertyName, ValueShape.SEQUENCE), key, replacement);
    }
    for (String value : values) {
      Optional<String> candidate = bindingCandidate(value);
      if (candidate.isPresent() && !isBindable(propertyMetadata.get(), candidate.get(), true)) {
        return withDeprecation(
            Optional.of(invalidValueFinding(key, effectivePropertyName, value)), key, replacement);
      }
    }
    return withDeprecation(Optional.empty(), key, replacement);
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
      return Optional.of(unsupportedScalarShapeFinding(key));
    }
    Optional<PropertyMetadata> propertyMetadata = CLIENT_PROPERTY_METADATA.property(propertyName);
    if (propertyMetadata.isPresent()) {
      if (valueShape == ValueShape.SEQUENCE && propertyMetadata.get().collection()) {
        return Optional.empty();
      }
      return Optional.of(
          propertyMetadata.get().collection()
              ? unsupportedSequenceShapeFinding(key)
              : unsupportedScalarShapeFinding(key));
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

  private static ClientPropertyMetadata clientPropertyMetadata() {
    try (JarFile jarFile = targetStarterJar();
        InputStream stream = resource(jarFile, METADATA_RESOURCE)) {
      JsonNode properties = OBJECT_MAPPER.readTree(stream).path("properties");
      Map<ConfigurationPropertyName, PropertyMetadata> clientProperties = new HashMap<>();
      Set<ConfigurationPropertyName> authProperties = new HashSet<>();
      for (JsonNode property : properties) {
        String name = property.path("name").asText();
        ConfigurationPropertyName propertyName = propertyName(name);
        if (CLIENT_PREFIX.isAncestorOf(propertyName)) {
          PropertyMetadata metadata = propertyMetadata(property.path("type").asText(""));
          clientProperties.put(propertyName, metadata);
          if (AUTH_PREFIX.isAncestorOf(propertyName)) {
            authProperties.add(propertyName);
          }
        }
      }
      return new ClientPropertyMetadata(Map.copyOf(clientProperties), Set.copyOf(authProperties));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Cannot load Camunda client configuration metadata from the target starter.", e);
    }
  }

  private static PropertyMetadata propertyMetadata(String type) {
    boolean collection = isCollectionType(type);
    return new PropertyMetadata(type, collection, collection ? collectionElementType(type) : type);
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

  private static String collectionElementType(String type) {
    if (type.endsWith("[]")) {
      return type.substring(0, type.length() - 2);
    }
    int genericStart = type.indexOf('<');
    int genericEnd = type.lastIndexOf('>');
    if (genericStart > -1 && genericEnd > genericStart + 1) {
      return type.substring(genericStart + 1, genericEnd).trim();
    }
    return "java.lang.String";
  }

  private static boolean isBindable(PropertyMetadata propertyMetadata, String value, boolean indexed) {
    try {
      String key = indexed && propertyMetadata.collection() ? "config[0]" : "config";
      Binder binder =
          new Binder(new MapConfigurationPropertySource(Map.of(key, value)));
      return binder.bind("config", propertyMetadata.bindable()).isBound();
    } catch (BindException e) {
      return false;
    }
  }

  static Optional<String> bindingCandidate(String value) {
    String candidate = value.trim();
    if (!candidate.contains("${")) {
      return Optional.of(candidate);
    }
    if (!candidate.startsWith("${") || !candidate.endsWith("}")) {
      return Optional.empty();
    }
    while (candidate.startsWith("${") && candidate.endsWith("}")) {
      int separator = placeholderDefaultSeparator(candidate);
      if (separator < 0) {
        return Optional.empty();
      }
      candidate = candidate.substring(separator + 1, candidate.length() - 1).trim();
    }
    return candidate.contains("${") ? Optional.empty() : Optional.of(candidate);
  }

  private static int placeholderDefaultSeparator(String value) {
    int nestedExpressions = 0;
    for (int i = 2; i < value.length() - 1; i++) {
      char current = value.charAt(i);
      if (current == '$' && i + 1 < value.length() - 1 && value.charAt(i + 1) == '{') {
        nestedExpressions++;
        i++;
        continue;
      }
      if (current == '}' && nestedExpressions > 0) {
        nestedExpressions--;
        continue;
      }
      if (current == ':' && nestedExpressions == 0) {
        return i;
      }
    }
    return -1;
  }

  private static String invalidValueFinding(
      String key, ConfigurationPropertyName propertyName, String value) {
    Optional<String> enumFinding = enumFinding(propertyName, value);
    if (enumFinding.isPresent()) {
      return enumFinding.get();
    }
    return "Invalid Camunda client configuration value '"
        + value
        + "' for '"
        + key
        + "'. Review it against the target Camunda Spring Boot starter type.";
  }

  private static Optional<String> enumFinding(
      ConfigurationPropertyName propertyName, String value) {
    Optional<String> candidate = bindingCandidate(value);
    if (candidate.isEmpty()) {
      return Optional.empty();
    }
    if (propertyName.equals(MODE) && isUnsupportedMode(candidate.get())) {
      return Optional.of(
          "Invalid Camunda client mode '"
              + value
              + "'. Use 'self-managed' or 'saas' for "
              + MODE
              + ".");
    }
    if (propertyName.equals(AUTH_METHOD) && isUnsupportedAuthMethod(candidate.get())) {
      return Optional.of(
          "Invalid Camunda client authentication method '"
              + value
              + "'. Use 'none', 'basic', or 'oidc' for "
              + AUTH_METHOD
              + ".");
    }
    return Optional.empty();
  }

  private static String unsupportedScalarShapeFinding(String key) {
    return "Unsupported Camunda client configuration shape for '" + key + "'. Use a scalar value.";
  }

  private static String unsupportedSequenceShapeFinding(String key) {
    return "Unsupported Camunda client configuration shape for '"
        + key
        + "'. Use a sequence value.";
  }

  private static Class<?> resolveClass(String typeName) throws ClassNotFoundException {
    Class<?> primitiveType = ClassUtils.resolvePrimitiveClassName(typeName);
    if (primitiveType != null) {
      return primitiveType;
    }
    if (typeName.endsWith("[]")) {
      return Array.newInstance(resolveClass(typeName.substring(0, typeName.length() - 2)), 0)
          .getClass();
    }
    return ClassUtils.forName(typeName, CamundaClientConfigurationValidation.class.getClassLoader());
  }

  private static String normalize(String value) {
    return value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
  }

  private record ClientPropertyMetadata(
      Map<ConfigurationPropertyName, PropertyMetadata> properties,
      Set<ConfigurationPropertyName> authProperties) {

    private Optional<PropertyMetadata> property(ConfigurationPropertyName propertyName) {
      return Optional.ofNullable(properties.get(propertyName));
    }
  }

  private record PropertyMetadata(String typeName, boolean collection, String itemTypeName) {

    private Bindable<?> bindable() {
      try {
        if (!collection || typeName.endsWith("[]")) {
          return Bindable.of(resolveClass(typeName));
        }
        int genericStart = typeName.indexOf('<');
        if (genericStart < 0) {
          return Bindable.of(resolveClass(typeName));
        }
        Class<?> rawType = resolveClass(typeName.substring(0, genericStart));
        return Bindable.of(
            ResolvableType.forClassWithGenerics(rawType, resolveClass(itemTypeName)));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Cannot resolve Spring configuration type " + typeName, e);
      }
    }
  }

  private record PropertyReference(ConfigurationPropertyName propertyName, boolean isIndexed) {}

  enum ValueShape {
    MAPPING,
    SEQUENCE
  }
}
