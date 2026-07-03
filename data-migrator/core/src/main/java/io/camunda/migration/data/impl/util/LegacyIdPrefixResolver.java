/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.util;

import static io.camunda.migration.data.constants.MigratorConstants.DEFAULT_LEGACY_ID_PREFIX;
import static io.camunda.migration.data.impl.logging.ConfigurationLogs.getLegacyIdPrefixBlankError;
import static io.camunda.migration.data.impl.logging.ConfigurationLogs.getLegacyIdPrefixInvalidCharsError;
import static io.camunda.migration.data.impl.logging.ConfigurationLogs.getLegacyIdPrefixTooLongError;
import static io.camunda.migration.data.impl.logging.ConfigurationLogs.logEffectiveLegacyIdPrefix;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.config.property.history.HistoryProperties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Resolves, validates and applies the prefix prepended to Camunda 7 historical definition IDs
 * (process, decision and form definitions) when they are migrated to Camunda 8.
 * <p>
 * This is the single place that owns the legacy ID prefix. All history migration components apply
 * the prefix through {@link #applyTo(String)} instead of concatenating the prefix themselves, so the
 * effective prefix stays consistent across every migrated definition type.
 * <p>
 * The prefix defaults to {@code c7-legacy-} and can be overridden with the
 * {@code camunda.migrator.history.legacy-id-prefix} property. When explicitly configured the value
 * is validated on startup and an invalid value fails fast:
 * <ul>
 *   <li>it must not be blank;</li>
 *   <li>it must not exceed {@value #MAX_PREFIX_LENGTH} characters;</li>
 *   <li>it must match {@link #ALLOWED_PREFIX_PATTERN}, i.e. start with an ASCII letter or underscore
 *       and otherwise contain only ASCII letters, digits, {@code '.'}, {@code '-'} and {@code '_'}.
 *       This keeps {@code prefix + <original definition id>} a valid XML NCName, which decision (DMN)
 *       definition IDs are re-parsed as during migration.</li>
 * </ul>
 * Prefixing avoids collisions between migrated history definitions and native Camunda 8 definitions
 * that share the same ID.
 */
public class LegacyIdPrefixResolver {

  /**
   * Maximum length allowed for the configured prefix. The prefix is prepended to the original
   * Camunda 7 definition ID; the combined value must stay within Camunda 8's 255-character
   * definition ID limit, so the prefix itself is capped well below that.
   */
  public static final int MAX_PREFIX_LENGTH = 100;

  /**
   * Allowed character set for the configured prefix. The value must start with an ASCII letter or
   * underscore and may otherwise contain ASCII letters, digits, {@code '.'}, {@code '-'} and
   * {@code '_'}.
   */
  public static final String ALLOWED_PREFIX_REGEX = "^[A-Za-z_][A-Za-z0-9._-]*$";

  protected static final Pattern ALLOWED_PREFIX_PATTERN = Pattern.compile(ALLOWED_PREFIX_REGEX);

  protected final String prefix;

  public LegacyIdPrefixResolver(MigratorProperties migratorProperties) {
    this.prefix = resolveAndValidate(configuredPrefix(migratorProperties));
    logEffectiveLegacyIdPrefix(this.prefix);
  }

  protected static String configuredPrefix(MigratorProperties migratorProperties) {
    HistoryProperties history = migratorProperties.getHistory();
    return history == null ? null : history.getLegacyIdPrefix();
  }

  /**
   * Resolves the effective prefix from the configured value, falling back to the default when it is
   * not configured, and validates an explicitly configured value.
   *
   * @param configuredPrefix the raw configured prefix, or {@code null} when not configured
   * @return the effective, validated prefix
   * @throws IllegalArgumentException if an explicitly configured prefix is blank, too long, or
   *                                  contains disallowed characters
   */
  protected static String resolveAndValidate(String configuredPrefix) {
    if (configuredPrefix == null) {
      return DEFAULT_LEGACY_ID_PREFIX;
    }
    if (configuredPrefix.isBlank()) {
      throw new IllegalArgumentException(getLegacyIdPrefixBlankError());
    }
    if (configuredPrefix.length() > MAX_PREFIX_LENGTH) {
      throw new IllegalArgumentException(getLegacyIdPrefixTooLongError(configuredPrefix, MAX_PREFIX_LENGTH));
    }
    if (!ALLOWED_PREFIX_PATTERN.matcher(configuredPrefix).matches()) {
      throw new IllegalArgumentException(getLegacyIdPrefixInvalidCharsError(configuredPrefix));
    }
    return configuredPrefix;
  }

  /**
   * @return the effective prefix, e.g. {@code c7-legacy-} by default.
   */
  public String getPrefix() {
    return prefix;
  }

  /**
   * Prepends the effective prefix to the given Camunda 7 definition ID.
   *
   * @param definitionId the original Camunda 7 definition ID, may be {@code null}
   * @return the prefixed definition ID, or {@code null} if the input was {@code null}
   */
  public String applyTo(String definitionId) {
    if (definitionId == null) {
      return null;
    }
    return prefix + definitionId;
  }
}
