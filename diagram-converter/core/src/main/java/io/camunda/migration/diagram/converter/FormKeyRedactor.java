/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
 * because a rewritten reference would no longer resolve. Redaction never affects how a form key is
 * classified.
 *
 * <p>Parameter names are normalized before matching, so separators, casing, and percent encoding do
 * not defeat detection: {@code id_token}, {@code X-API-Key}, and {@code api%5Fkey} are all
 * recognized. Because no name list can be complete, a value shaped like a JSON Web Token — signed
 * (JWS) or encrypted (JWE) compact serialization, detected by decoding its JOSE header — is
 * redacted whatever its parameter is called. This is best-effort hygiene for a shared report, not a
 * secret scanner; the authoritative protection is that the raw value never leaves the model.
 */
public final class FormKeyRedactor {

  public static final String REDACTED = "<redacted>";

  /** Normalized parameter names that are credentials on their own but unsafe as substrings. */
  private static final Set<String> CREDENTIAL_NAMES = Set.of("key", "sig", "auth", "pw");

  /** Fragments that identify a credential anywhere in a normalized parameter name. */
  private static final List<String> CREDENTIAL_NAME_FRAGMENTS =
      List.of(
          "accesskey",
          "apikey",
          "assertion",
          "authorization",
          "bearer",
          "credential",
          "jwt",
          "oauth",
          "passwd",
          "password",
          "privatekey",
          "pwd",
          "secret",
          "session",
          "sharedkey",
          "signature",
          "signingkey",
          "token");

  /**
   * Segments of a compact-serialized token: unpadded base64url, possibly empty for a detached
   * payload, an {@code alg:none} signature, or a {@code dir} encrypted key.
   */
  private static final Pattern COMPACT_TOKEN_SEGMENT = Pattern.compile("[A-Za-z0-9_-]*");

  private static final Pattern USER_INFO_PASSWORD =
      Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*://[^/?#@]*?:)([^/?#@]*)(@)");

  private FormKeyRedactor() {}

  /**
   * Redacts credential-like parts of a form key for reporting.
   *
   * <p>Redacts the password of a {@code scheme://user:password@host} reference, the value of any
   * query parameter whose normalized name identifies a credential, and any query value shaped like
   * a JSON Web Token. Everything else is preserved byte for byte so the finding still identifies
   * the form.
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
    String parameterValue = parameter.substring(separator + 1);
    if (isCredentialName(name) || isJsonWebToken(parameterValue)) {
      return name + "=" + REDACTED;
    }
    return parameter;
  }

  private static boolean isCredentialName(String name) {
    String normalized = normalize(name);
    if (normalized.isEmpty()) {
      return false;
    }
    if (CREDENTIAL_NAMES.contains(normalized)) {
      return true;
    }
    return CREDENTIAL_NAME_FRAGMENTS.stream().anyMatch(normalized::contains);
  }

  /**
   * Recognizes a compact-serialized JSON Web Token: three segments for a signed JWS, five for an
   * encrypted JWE, with a first segment that decodes to a JSON object.
   *
   * <p>The header is decoded rather than prefix-matched. JSON permits leading whitespace, so a
   * header encoding {@code {"alg":"HS256"}} starts with {@code IHsi} rather than the familiar
   * {@code eyJ}, and a prefix check would export it verbatim.
   */
  private static boolean isJsonWebToken(String parameterValue) {
    String value = decode(parameterValue);
    String[] segments = value.split("\\.", -1);
    if (segments.length != 3 && segments.length != 5) {
      return false;
    }
    for (String segment : segments) {
      if (!COMPACT_TOKEN_SEGMENT.matcher(segment).matches()) {
        return false;
      }
    }
    return isJoseHeader(segments[0]);
  }

  private static boolean isJoseHeader(String segment) {
    if (segment.isEmpty()) {
      return false;
    }
    try {
      String header =
          new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8).strip();
      return header.startsWith("{") && header.endsWith("}");
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** Lowercases and strips everything that is not a letter or digit, after percent decoding. */
  private static String normalize(String name) {
    String decoded = decode(name);
    StringBuilder normalized = new StringBuilder(decoded.length());
    for (int i = 0; i < decoded.length(); i++) {
      char character = decoded.charAt(i);
      if (Character.isLetterOrDigit(character)) {
        normalized.append(character);
      }
    }
    return normalized.toString().toLowerCase(Locale.ROOT);
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }
}
