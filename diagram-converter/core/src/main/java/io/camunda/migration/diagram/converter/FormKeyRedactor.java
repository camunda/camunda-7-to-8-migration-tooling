/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Removes credential-like values from a Camunda 7 {@code camunda:formKey} before it is rendered
 * into a conversion finding.
 *
 * <p>An External Task Form key is frequently a URL, and analysis findings are exported to CSV,
 * JSON, XLSX, and markdown reports that are shared independently of the BPMN. Copying a query value
 * such as {@code ?token=...} into those artifacts duplicates the secret into a wider audience than
 * the model itself.
 *
 * <p>Only the reported value is redacted. The converted BPMN keeps the exact original reference,
 * because a rewritten reference would no longer resolve.
 */
public final class FormKeyRedactor {

  public static final String REDACTED = "<redacted>";

  private static final Set<String> CREDENTIAL_PARAMETER_NAMES =
      Set.of(
          "access_token",
          "accesstoken",
          "api_key",
          "apikey",
          "auth",
          "authorization",
          "client_secret",
          "clientsecret",
          "credential",
          "credentials",
          "key",
          "passwd",
          "password",
          "pwd",
          "refresh_token",
          "refreshtoken",
          "secret",
          "session",
          "sessionid",
          "sig",
          "signature",
          "token");

  private static final Pattern USER_INFO_PASSWORD =
      Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*://[^/?#@]*?:)([^/?#@]*)(@)");

  private FormKeyRedactor() {}

  /**
   * Redacts credential-like parts of a form key for reporting.
   *
   * <p>Redacts the password of a {@code scheme://user:password@host} reference and the value of any
   * query parameter whose name is a well-known credential name. Everything else is preserved byte
   * for byte so the finding still identifies the form.
   *
   * @param formKey the raw attribute value, may be {@code null}
   * @return the value safe to render into a report, never {@code null}
   */
  public static String redact(String formKey) {
    String value = StringUtils.defaultString(formKey);
    return redactQuery(redactUserInfoPassword(value));
  }

  private static String redactUserInfoPassword(String value) {
    Matcher matcher = USER_INFO_PASSWORD.matcher(value);
    if (!matcher.find()) {
      return value;
    }
    return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + REDACTED) + "$3");
  }

  private static String redactQuery(String value) {
    int queryStart = value.indexOf('?');
    if (queryStart < 0) {
      return value;
    }
    int queryEnd = value.indexOf('#', queryStart);
    if (queryEnd < 0) {
      queryEnd = value.length();
    }
    String query = value.substring(queryStart + 1, queryEnd);
    if (query.isEmpty()) {
      return value;
    }
    StringBuilder redacted = new StringBuilder(value.length());
    String[] parameters = query.split("&", -1);
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        redacted.append('&');
      }
      redacted.append(redactParameter(parameters[i]));
    }
    return value.substring(0, queryStart + 1) + redacted + value.substring(queryEnd);
  }

  private static String redactParameter(String parameter) {
    int separator = parameter.indexOf('=');
    if (separator < 0) {
      return parameter;
    }
    String name = parameter.substring(0, separator);
    if (!CREDENTIAL_PARAMETER_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
      return parameter;
    }
    return name + "=" + REDACTED;
  }
}
