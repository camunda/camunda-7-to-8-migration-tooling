/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.bpmn.BpmnTestcaseUtils;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormKeyRedactorTest {

  private static final String SECRET = "sup3r-s3cret-value";
  private static final String JWT =
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FuLWFwcHJvdmVyIn0.c2lnbmF0dXJlLXZhbHVl";
  private static final String JWE =
      "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ"
          + ".ZW5jcnlwdGVkLWtleQ.aXYtdmFsdWU.Y2lwaGVydGV4dA.dGFn";
  private static final String JWE_DIRECT =
      "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIn0..aXYtdmFsdWU.Y2lwaGVydGV4dA.dGFn";
  private static final String JWT_WITH_PADDED_HEADER =
      base64Url(" {\"alg\":\"HS256\"}")
          + "."
          + base64Url("{\"sub\":\"loan-approver\"}")
          + "."
          + base64Url("signature-value");

  /**
   * Runs the real converter over a user task and a start event carrying {@code formKey} and returns
   * every finding it produced.
   *
   * <p>This drives the production visitor chain deliberately. Calling {@link FormKeyRedactor}
   * directly here would make the no-leak tests self-fulfilling: they would keep passing if a
   * visitor later handed the raw form key to its message factory.
   */
  private static List<String> allFormKeyFindings(String formKey) {
    String snippet =
        """
        <bpmn:userTask id="FormKeyTask" camunda:formKey="%1$s"/>
        <bpmn:startEvent id="FormKeyStart" camunda:formKey="%1$s"/>
        """
            .formatted(formKey.replace("&", "&amp;").replace("\"", "&quot;"));

    DiagramCheckResult result =
        DiagramConverterFactory.getInstance()
            .get()
            .check(
                "form-key-leak-check.bpmn",
                BpmnTestcaseUtils.wrapSnippetInProcess(snippet),
                ConverterPropertiesFactory.getInstance().merge(new DefaultConverterProperties()));

    List<String> messages =
        result.getResults().stream()
            .flatMap(element -> element.getMessages().stream())
            .map(DiagramCheckResult.ElementCheckMessage::getMessage)
            .toList();

    assertThat(messages)
        .as("the converter must actually report both form key owners")
        .hasSizeGreaterThanOrEqualTo(2);
    return messages;
  }

  private static Stream<String> credentialBearingFormKeys() {
    return Stream.of(
        "https://forms.example.com/loan?token=" + SECRET,
        "https://forms.example.com/loan?taskId=1&access_token=" + SECRET,
        "https://forms.example.com/loan?API_KEY=" + SECRET + "&view=full",
        "https://forms.example.com/loan?signature=" + SECRET + "#section",
        "https://user:" + SECRET + "@forms.example.com/loan",
        "embedded:app:forms/loan.html?secret=" + SECRET,
        "camunda-forms:deployment:loan.form?password=" + SECRET,
        "${base}/loan?client_secret=" + SECRET,
        // standard OIDC/OAuth names the first denylist missed
        "https://forms.example.com/loan?id_token=" + SECRET,
        "https://forms.example.com/loan?refresh_token=" + SECRET,
        "https://forms.example.com/loan?oauth_signature=" + SECRET,
        "https://forms.example.com/loan?SAMLassertion=" + SECRET,
        // separator, casing, and percent-encoding variants
        "https://forms.example.com/loan?X-API-Key=" + SECRET,
        "https://forms.example.com/loan?access-token=" + SECRET,
        "https://forms.example.com/loan?Api%5FKey=" + SECRET,
        "https://forms.example.com/loan?JSESSIONID=" + SECRET,
        "https://forms.example.com/loan?pw=" + SECRET,
        "https://forms.example.com/loan?pass=" + SECRET,
        "https://forms.example.com/loan?otp=" + SECRET,
        "https://forms.example.com/loan?PIN=" + SECRET,
        "https://forms.example.com/loan?bearer=" + SECRET);
  }

  /** A JWT is redacted whatever its parameter is called, because no name list can be complete. */
  private static Stream<String> jsonWebTokenFormKeys() {
    return Stream.of(
        "https://forms.example.com/loan?t=" + JWT,
        "https://forms.example.com/loan?q1=" + JWT + "&view=full",
        "https://forms.example.com/loan?assertionBlob=" + JWT);
  }

  /**
   * JWE compact serialization has five segments, and a 'dir' encrypted key is legitimately empty.
   */
  private static Stream<String> jsonWebEncryptionFormKeys() {
    return Stream.of(
        "https://forms.example.com/loan?t=" + JWE,
        "https://forms.example.com/loan?opaque=" + JWE_DIRECT + "&view=full");
  }

  /**
   * JSON permits leading whitespace, so a JOSE header encoding {@code {"alg":"HS256"}} does not
   * start with the familiar {@code eyJ}. Such a token must still be recognized.
   */
  private static Stream<String> whitespacePaddedHeaderFormKeys() {
    return Stream.of(
        "https://forms.example.com/loan?t=" + JWT_WITH_PADDED_HEADER,
        "https://forms.example.com/loan?blob=" + JWT_WITH_PADDED_HEADER + "&view=full");
  }

  private static String base64Url(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @MethodSource("credentialBearingFormKeys")
  void shouldNeverLeakACredentialThroughAnyFormKeyFinding(String formKey) {
    assertThat(allFormKeyFindings(formKey))
        .as("form key findings for '%s'", formKey)
        .allSatisfy(message -> assertThat(message).doesNotContain(SECRET));
  }

  @ParameterizedTest
  @MethodSource("credentialBearingFormKeys")
  void shouldStillIdentifyTheFormAfterRedaction(String formKey) {
    assertThat(FormKeyRedactor.redact(formKey))
        .contains(FormKeyRedactor.REDACTED)
        .doesNotContain(SECRET);
  }

  @ParameterizedTest
  @MethodSource("jsonWebTokenFormKeys")
  void shouldNeverLeakAJsonWebTokenThroughAnyFormKeyFinding(String formKey) {
    assertThat(allFormKeyFindings(formKey))
        .as("form key findings for '%s'", formKey)
        .allSatisfy(message -> assertThat(message).doesNotContain(JWT));
  }

  @ParameterizedTest
  @MethodSource("jsonWebEncryptionFormKeys")
  void shouldNeverLeakAnEncryptedJsonWebTokenThroughAnyFormKeyFinding(String formKey) {
    assertThat(allFormKeyFindings(formKey))
        .as("form key findings for '%s'", formKey)
        .allSatisfy(message -> assertThat(message).doesNotContain(JWE).doesNotContain(JWE_DIRECT));
  }

  @ParameterizedTest
  @MethodSource("whitespacePaddedHeaderFormKeys")
  void shouldNeverLeakATokenWhoseHeaderStartsWithJsonWhitespace(String formKey) {
    assertThat(allFormKeyFindings(formKey))
        .as("form key findings for '%s'", formKey)
        .allSatisfy(message -> assertThat(message).doesNotContain(JWT_WITH_PADDED_HEADER));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "https://forms.example.com/loan?token=abc|https://forms.example.com/loan?token=<redacted>",
        "https://forms.example.com/loan?taskId=1&token=abc&view=full|https://forms.example.com/loan?taskId=1&token=<redacted>&view=full",
        "https://forms.example.com/loan?Token=abc|https://forms.example.com/loan?Token=<redacted>",
        "https://forms.example.com/loan?X-API-Key=abc|https://forms.example.com/loan?X-API-Key=<redacted>",
        "https://forms.example.com/loan?id_token=abc|https://forms.example.com/loan?id_token=<redacted>",
        "https://forms.example.com/loan?Api%5FKey=abc|https://forms.example.com/loan?Api%5FKey=<redacted>",
        "https://forms.example.com/loan?token=abc#top|https://forms.example.com/loan?token=<redacted>#top",
        "https://user:pw@forms.example.com/loan|https://user:<redacted>@forms.example.com/loan",
        "https://user@forms.example.com/loan|https://user@forms.example.com/loan"
      })
  void shouldRedactOnlyTheCredentialPart(String formKey, String expected) {
    assertThat(FormKeyRedactor.redact(formKey)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "embedded:app:forms/loan-approval.html",
        "camunda-forms:deployment:forms/userTask.form",
        "https://forms.example.com/loan",
        "https://forms.example.com/loan?taskId=1&view=full",
        "https://forms.example.com/loan?flag",
        // benign names that a naive substring denylist would over-redact
        "https://forms.example.com/loan?keyword=loan&monkey=1&sigma=2&author=jane&compass=north",
        "loanApprovalForm",
        "${formKey}"
      })
  void shouldPreserveFormKeysWithoutCredentials(String formKey) {
    assertThat(FormKeyRedactor.redact(formKey)).isEqualTo(formKey);
  }

  @Test
  void shouldTolerateAbsentFormKeys() {
    assertThat(FormKeyRedactor.redact(null)).isEmpty();
    assertThat(FormKeyRedactor.redact("")).isEmpty();
  }

  @Test
  void shouldNotRedactWhenThereIsNoQuery() {
    assertThat(FormKeyRedactor.redact("token=abc")).isEqualTo("token=abc");
  }

  @Test
  void shouldExerciseEveryFormKeyCategoryInTheLeakInputs() {
    assertThat(credentialBearingFormKeys().map(FormKeyType::of).distinct())
        .as("the no-leak inputs must reach every converter form key path")
        .containsExactlyInAnyOrder(FormKeyType.values());
  }

  @Test
  void shouldKeepTheVerbatimValueAvailableForTheConvertedModel() {
    String formKey = "https://forms.example.com/loan?token=" + SECRET;

    Message finding = MessageFactory.formKey("formKey", "userTask", formKey, FormKeyType.EXTERNAL);

    assertThat(finding.getMessage()).doesNotContain(SECRET);
    assertThat(FormKeyType.of(formKey))
        .as("redaction must not change how the form key is classified")
        .isEqualTo(FormKeyType.EXTERNAL);
  }
}
