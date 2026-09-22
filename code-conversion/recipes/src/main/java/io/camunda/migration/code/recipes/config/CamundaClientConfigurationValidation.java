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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
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
  private static final Pattern COLLECTION_INDEX_PATTERN = Pattern.compile("\\[\\d+\\]");
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
    boolean dynamicMapProperty =
        CLIENT_PROPERTY_METADATA.mapProperty(propertyName).isPresent();
    if (propertyMetadata.isEmpty() && !dynamicMapProperty) {
      return Optional.empty();
    }
    if (propertyMetadata.filter(PropertyMetadata::map).isPresent()) {
      return Optional.of(unsupportedMappingShapeFinding(key));
    }
    if (indexed
        && !dynamicMapProperty
        && propertyMetadata.filter(metadata -> !metadata.collection()).isPresent()) {
      return Optional.of(unsupportedScalarShapeFinding(key));
    }
    Optional<String> candidate = bindingCandidate(value);
    if (candidate.isEmpty()) {
      return enumFinding(propertyName, value);
    }
    return !isBindable(propertyName, candidate.get(), indexed)
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
    boolean dynamicMapProperty =
        CLIENT_PROPERTY_METADATA.mapProperty(effectivePropertyName).isPresent();
    boolean map = propertyMetadata.map(PropertyMetadata::map).orElse(false);
    boolean collection = propertyMetadata.map(PropertyMetadata::collection).orElse(false);
    if (map || (!collection && !dynamicMapProperty)) {
      return withDeprecation(
          shapeFinding(key, effectivePropertyName, ValueShape.SEQUENCE), key, replacement);
    }
    for (String value : values) {
      Optional<String> candidate = bindingCandidate(value);
      if (candidate.isPresent()
          && !isBindable(effectivePropertyName, candidate.get(), true)) {
        return withDeprecation(
            Optional.of(invalidValueFinding(key, effectivePropertyName, value)), key, replacement);
      }
    }
    return withDeprecation(Optional.empty(), key, replacement);
  }

  static Optional<String> sequenceElementFinding(String key, ValueShape elementShape) {
    PropertyReference propertyReference = propertyReference(key);
    ConfigurationPropertyName propertyName = propertyReference.propertyName();
    String replacement = LEGACY_PROPERTY_MAPPINGS.get(propertyName);
    ConfigurationPropertyName effectivePropertyName =
        replacement == null ? propertyName : propertyName(replacement);
    Optional<PropertyMetadata> propertyMetadata =
        CLIENT_PROPERTY_METADATA.property(effectivePropertyName);
    boolean dynamicMapProperty =
        CLIENT_PROPERTY_METADATA.mapProperty(effectivePropertyName).isPresent();
    boolean collection = propertyMetadata.map(PropertyMetadata::collection).orElse(false);
    if (!collection && !dynamicMapProperty) {
      return withDeprecation(
          shapeFinding(key, effectivePropertyName, ValueShape.SEQUENCE), key, replacement);
    }
    if (propertyMetadata.filter(PropertyMetadata::simpleCollection).isPresent()) {
      return withDeprecation(
          Optional.of(unsupportedSequenceElementShapeFinding(key)), key, replacement);
    }
    Object value =
        elementShape == ValueShape.MAPPING ? Map.of("value", "example") : List.of("example");
    return withDeprecation(
        isBindable(effectivePropertyName, value, true)
            ? Optional.empty()
            : Optional.of(unsupportedSequenceElementShapeFinding(key)),
        key,
        replacement);
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
      if (propertyMetadata.get().map()) {
        return valueShape == ValueShape.MAPPING
            ? Optional.empty()
            : Optional.of(unsupportedMappingShapeFinding(key));
      }
      if (valueShape == ValueShape.SEQUENCE && propertyMetadata.get().collection()) {
        return Optional.empty();
      }
      return Optional.of(
          propertyMetadata.get().collection()
              ? unsupportedSequenceShapeFinding(key)
              : unsupportedScalarShapeFinding(key));
    }
    if (valueShape == ValueShape.MAPPING
        && CLIENT_PROPERTY_METADATA.mapProperty(propertyName).isPresent()) {
      return Optional.empty();
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
    return new PropertyMetadata(
        collection, isMapType(type), collection && isSimpleType(collectionElementType(type)));
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
    String normalizedKey = COLLECTION_INDEX_PATTERN.matcher(key).replaceAll("");
    return new PropertyReference(propertyName(normalizedKey), !normalizedKey.equals(key));
  }

  private static boolean isCollectionType(String type) {
    return type.endsWith("[]")
        || type.startsWith("java.lang.Iterable")
        || type.startsWith("java.util.Collection")
        || type.startsWith("java.util.List")
        || type.startsWith("java.util.Set");
  }

  private static boolean isMapType(String type) {
    return type.equals("java.util.Map") || type.startsWith("java.util.Map<");
  }

  private static String collectionElementType(String type) {
    if (type.endsWith("[]")) {
      return type.substring(0, type.length() - 2);
    }
    int genericStart = type.indexOf('<');
    int genericEnd = type.lastIndexOf('>');
    return genericStart > -1 && genericEnd > genericStart + 1
        ? type.substring(genericStart + 1, genericEnd).trim()
        : "";
  }

  private static boolean isSimpleType(String type) {
    if (type.isEmpty() || isCollectionType(type) || isMapType(type)) {
      return false;
    }
    int genericStart = type.indexOf('<');
    String rawType = genericStart < 0 ? type : type.substring(0, genericStart);
    Class<?> resolvedType = ClassUtils.resolvePrimitiveClassName(rawType);
    try {
      if (resolvedType == null) {
        resolvedType =
            ClassUtils.forName(
                rawType, CamundaClientConfigurationValidation.class.getClassLoader());
      }
      return BeanUtils.isSimpleProperty(resolvedType);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot resolve Spring configuration type " + type, e);
    }
  }

  private static boolean isBindable(
      ConfigurationPropertyName propertyName, Object value, boolean indexed) {
    try {
      String key = propertyName + (indexed ? "[0]" : "");
      Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(key, value)));
      // Bind the owning type so validation performed by its property setters is preserved.
      return binder.bind(CLIENT_PREFIX.toString(), Bindable.of(CamundaClientProperties.class))
          .isBound();
    } catch (BindException e) {
      return false;
    }
  }

  private static Optional<String> bindingCandidate(String value) {
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

  private static String unsupportedMappingShapeFinding(String key) {
    return "Unsupported Camunda client configuration shape for '" + key + "'. Use a mapping value.";
  }

  private static String unsupportedSequenceShapeFinding(String key) {
    return "Unsupported Camunda client configuration shape for '"
        + key
        + "'. Use a sequence value.";
  }

  private static String unsupportedSequenceElementShapeFinding(String key) {
    return "Unsupported Camunda client configuration shape for '"
        + key
        + "'. Sequence elements must match the target collection element type.";
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

    private Optional<PropertyMetadata> mapProperty(ConfigurationPropertyName propertyName) {
      return properties.entrySet().stream()
          .filter(entry -> entry.getValue().map() && entry.getKey().isAncestorOf(propertyName))
          .map(Map.Entry::getValue)
          .findFirst();
    }
  }

  private record PropertyMetadata(boolean collection, boolean map, boolean simpleCollection) {}

  private record PropertyReference(ConfigurationPropertyName propertyName, boolean isIndexed) {}

  enum ValueShape {
    MAPPING,
    SEQUENCE
  }
}
