/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.webapp;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import io.camunda.migration.diagram.converter.DiagramCheckResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ConverterControllerTest {
  private static final Logger LOG = LoggerFactory.getLogger(ConverterControllerTest.class);
  @LocalServerPort int port;

  @BeforeEach
  void setup() {
    RestAssured.port = port;
  }

  @Test
  void singleBpmnCheckWithJsonResult() throws URISyntaxException {
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept(ContentType.JSON)
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult)
        .hasSize(1)
        .first()
        .matches(result -> result.getFilename().equals("example.bpmn"), "Filename is set correctly")
        .matches(result -> result.getResults().size() > 0, "Found results");
  }

  @Test
  void multipleFilesCheck() throws URISyntaxException {
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example2.bpmn").toURI()))
            .accept(ContentType.JSON)
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult)
        .anySatisfy(
            singleCheckResult -> {
              assertThat(singleCheckResult.getFilename()).isEqualTo("example.bpmn");
              assertThat(singleCheckResult.getResults())
                  .isNotEmpty()
                  .anySatisfy(
                      result -> assertThat(result.getElementId()).isEqualTo("Activity_Example1"));
            })
        .anySatisfy(
            singleCheckResult -> {
              assertThat(singleCheckResult.getFilename()).isEqualTo("example2.bpmn");
              assertThat(singleCheckResult.getResults())
                  .isNotEmpty()
                  .anySatisfy(
                      result -> assertThat(result.getElementId()).isEqualTo("Activity_Example2"));
            });
  }

  @Test
  void singleBpmnCheckWithJsonResultAndParameterizedAcceptHeader() throws URISyntaxException {
    // parameterized application/json must still negotiate to the nested preview JSON, not 400
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("application/json; charset=UTF-8")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult).isNotEmpty();
  }

  @Test
  void singleBpmnCheckWithJsonResultAndWildcardAcceptHeader() throws URISyntaxException {
    // JSON is the default representation; a generic client sending */* or application/* must not
    // get a 400
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("*/*")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult).isNotEmpty();
  }

  @Test
  void singleBpmnCheckWithAnalysisJsonResult() throws URISyntaxException {
    List<Map<String, String>> report =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept(ConverterController.APPLICATION_ANALYSIS_JSON)
            .post("/check")
            .then()
            .header("Content-Disposition", containsString("analysis-results.json"))
            .header(
                "Content-Type",
                equalTo(ConverterController.APPLICATION_ANALYSIS_JSON + ";charset=UTF-8"))
            .extract()
            .as(new TypeRef<List<Map<String, String>>>() {});

    assertThat(report)
        .isNotEmpty()
        .first()
        .satisfies(entry -> assertThat(entry.get("filename")).isEqualTo("example.bpmn"))
        .matches(
            entry ->
                entry
                    .keySet()
                    .containsAll(
                        List.of(
                            "elementName",
                            "elementId",
                            "elementType",
                            "severity",
                            "messageId",
                            "message",
                            "link")),
            "Entry carries the full flat report fields");
  }

  @Test
  void singleBpmnCheckWithAnalysisJsonResultAcceptsParameterizedAcceptHeader()
      throws URISyntaxException {
    // clients may append parameters such as charset or q-values to the Accept header
    // (malformed Accept values are rejected by Spring's produces negotiation with 406 before the
    // controller runs)
    List<Map<String, String>> report =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept(
                ConverterController.APPLICATION_ANALYSIS_JSON
                    + "; charset=UTF-8, application/json;q=0.8")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<Map<String, String>>>() {});

    // the flat report carries messageId per entry; the nested preview JSON would not
    assertThat(report)
        .isNotEmpty()
        .first()
        .satisfies(entry -> assertThat(entry).containsKey("messageId"));
  }

  @Test
  void singleBpmnCheckWithCsvResult() throws URISyntaxException, IOException {
    String body =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .multiPart("addDataMigrationExecutionListener", "false")
            .accept("text/csv")
            .post("/check")
            .getBody()
            .print();
    try (CSVReader reader =
        new CSVReaderBuilder(new StringReader(body))
            .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
            .build()) {
      List<String[]> all = reader.readAll();
      assertThat(all).hasSize(2);
    } catch (CsvException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void singleBpmnCheckWithCsvResultAndParameterizedAcceptHeader()
      throws URISyntaxException, IOException {
    String body =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("text/csv; charset=UTF-8")
            .post("/check")
            .getBody()
            .print();
    try (CSVReader reader =
        new CSVReaderBuilder(new StringReader(body))
            .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
            .build()) {
      assertThat(reader.readAll()).isNotEmpty();
    } catch (CsvException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void singleBpmnCheckWithCsvResultAndTextWildcardAcceptHeader()
      throws URISyntaxException, IOException {
    // text/* resolves to the supported text/csv representation
    String body =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("text/*")
            .post("/check")
            .getBody()
            .print();
    try (CSVReader reader =
        new CSVReaderBuilder(new StringReader(body))
            .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
            .build()) {
      assertThat(reader.readAll()).isNotEmpty();
    } catch (CsvException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void singleBpmnCheckWithJsonResultAndApplicationWildcardAcceptHeader() throws URISyntaxException {
    // application/* resolves to the default application/json representation
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("application/*")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult).isNotEmpty();
  }

  @Test
  void singleBpmnCheckWithExcelResult() throws Exception {
    byte[] response =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            //	      .accept("application/vnd.ms-excel")
            //	      .accept("application/excel")
            .post("/check")
            .getBody()
            .asByteArray();

    // Validate Excel using Apache POI
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response))) {
      assertThat(workbook.getNumberOfSheets()).isGreaterThan(0);
    }
  }

  @Test
  void multipleBpmnCheckWithExcelResult() throws Exception {
    byte[] response =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example2.bpmn").toURI()))
            .multiPart("addDataMigrationExecutionListener", "false")
            .accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            //	      .accept("application/vnd.ms-excel")
            //	      .accept("application/excel")
            .post("/check")
            .getBody()
            .asByteArray();

    // Validate Excel using Apache POI
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response))) {
      XSSFSheet sheet = workbook.getSheet("AnalysisResults");
      assertThat(sheet).as("Sheet 'AnalysisResults' should exist").isNotNull();

      // Collect values from rows 1 and 2, column 0
      String filename1 = sheet.getRow(1).getCell(0).getStringCellValue();
      String filename2 = sheet.getRow(2).getCell(0).getStringCellValue();

      // Assert both expected filenames are present, order-independent
      assertThat(List.of(filename1, filename2))
          .containsExactlyInAnyOrder("example.bpmn", "example2.bpmn");
    }
  }

  @Test
  void singleBpmnCheckWithUnsupportedHigherQualityFallsBackToSupported() throws URISyntaxException {
    // an unsupported media type with higher q must not win over a supported one
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("text/html;q=1.0, application/json;q=0.8")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult).isNotEmpty();
  }

  @Test
  void singleBpmnCheckWithExcelResultAndParameterizedAcceptHeader() throws Exception {
    byte[] response =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; charset=UTF-8")
            .post("/check")
            .getBody()
            .asByteArray();

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response))) {
      assertThat(workbook.getNumberOfSheets()).isGreaterThan(0);
    }
  }

  @Test
  void singleBpmnCheckWithJsonResultDefaultsToJsonWhenNoBestMatch() throws URISyntaxException {
    // a structured-syntax suffix Accept (application/*+json) is compatible with application/json;
    // no supported best match exists, so the endpoint defaults to JSON rather than a 400
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("application/*+json")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult).isNotEmpty();
  }

  @Test
  void singleBpmnCheckWithJsonResultPrefersJsonSuffixOverLowerQualityCsv()
      throws URISyntaxException {
    // a higher-q structured-suffix JSON wildcard must win over a lower-q concrete type
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("application/*+json;q=1.0, text/csv;q=0.8")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult).isNotEmpty();
  }

  @Test
  void singleBpmnCheckWithConcreteJsonSuffixPrefersSupportedVendorType() throws URISyntaxException {
    // a concrete suffix type like application/problem+json is not application/json-compatible;
    // the supported vendor type must win
    List<Map<String, String>> report =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept(
                "application/problem+json;q=1.0, "
                    + ConverterController.APPLICATION_ANALYSIS_JSON
                    + ";q=0.9")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<Map<String, String>>>() {});

    // flat report shape (messageId key) proves the vendor representation was selected
    assertThat(report)
        .isNotEmpty()
        .first()
        .satisfies(entry -> assertThat(entry).containsKey("messageId"));
  }

  @Test
  void singleBpmnCheckWithUnsatisfiableWildcardPrefersSupportedVendorType()
      throws URISyntaxException {
    // image/* cannot be satisfied by any produces type; the supported vendor type must win
    List<Map<String, String>> report =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .accept("image/*;q=1.0, " + ConverterController.APPLICATION_ANALYSIS_JSON + ";q=0.9")
            .post("/check")
            .getBody()
            .as(new TypeRef<List<Map<String, String>>>() {});

    // flat report shape (messageId key) proves the vendor representation was selected
    assertThat(report)
        .isNotEmpty()
        .first()
        .satisfies(entry -> assertThat(entry).containsKey("messageId"));
  }

  @Test
  void convertBpmn() throws URISyntaxException {
    byte[] bpmn =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .formParam("appendDocumentation", true)
            .accept("application/bpmn+xml")
            .post("/convert")
            .getBody()
            .asByteArray();
    ByteArrayInputStream in = new ByteArrayInputStream(bpmn);
    LOG.info("{}", new String(bpmn));
    BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(in);
    DomElement process = bpmnModelInstance.getDocument().getElementById("Process_11j5dku");
    assertThat(process).isNotNull();
  }

  @Test
  void convertDmn() throws URISyntaxException {
    byte[] bpmn =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("first.dmn").toURI()))
            .formParam("appendDocumentation", true)
            .accept("application/dmn+xml")
            .post("/convert")
            .getBody()
            .asByteArray();
    ByteArrayInputStream in = new ByteArrayInputStream(bpmn);
    LOG.info("{}", new String(bpmn));
    DmnModelInstance bpmnModelInstance = Dmn.readModelFromStream(in);
    DomElement decision = bpmnModelInstance.getDocument().getElementById("Decision_0kjih6z");
    assertThat(decision).isNotNull();
  }

  @Test
  void convertBpmnBatch() throws URISyntaxException, IOException {
    byte[] zip =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example2.bpmn").toURI()))
            .formParam("appendDocumentation", true)
            .accept("application/zip")
            .post("/convertBatch")
            .getBody()
            .asByteArray();

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      int entryCount = 0;
      ZipEntry zipEntry;
      while ((zipEntry = zis.getNextEntry()) != null) {
        entryCount++;

        if (entryCount == 1) {
          assertThat(zipEntry.getName()).isEqualTo("converted-c8-example.bpmn");

          ByteArrayInputStream in = new ByteArrayInputStream(zis.readAllBytes());
          BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(in);

          DomElement process = bpmnModelInstance.getDocument().getElementById("Process_11j5dku");
          assertThat(process).isNotNull();
        } else if (entryCount == 2) {
          assertThat(zipEntry.getName()).isEqualTo("converted-c8-example2.bpmn");

          ByteArrayInputStream in = new ByteArrayInputStream(zis.readAllBytes());
          BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(in);

          DomElement process = bpmnModelInstance.getDocument().getElementById("Process_Example2");
          assertThat(process).isNotNull();
        }

        zis.closeEntry();
      }

      // Final assertions
      assertThat(entryCount).as("There should be exactly 2 entries in the zip").isEqualTo(2);
    }
  }

  @Test
  void checkFormReturnsEmptyResult() throws URISyntaxException {
    List<DiagramCheckResult> checkResult =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("simple.form").toURI()))
            .accept(ContentType.JSON)
            .post("/check")
            .getBody()
            .as(new TypeRef<List<DiagramCheckResult>>() {});

    assertThat(checkResult)
        .hasSize(1)
        .first()
        .matches(result -> result.getFilename().equals("simple.form"), "Filename is set correctly")
        .matches(result -> result.getResults().isEmpty(), "Forms have no diagram elements");
  }

  @Test
  void convertForm() throws URISyntaxException {
    byte[] form =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("simple.form").toURI()))
            .accept(ContentType.JSON)
            .post("/convert")
            .getBody()
            .asByteArray();

    String converted = new String(form, StandardCharsets.UTF_8);
    assertThat(converted).contains("\"executionPlatform\": \"Camunda Cloud\"");
    assertThat(converted).contains("\"executionPlatformVersion\": \"8.9.0\"");
    assertThat(converted).contains("\"customerName\"");
  }

  @Test
  void convertFormRespectsPlatformVersionParam() throws URISyntaxException {
    byte[] form =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("simple.form").toURI()))
            .formParam("platformVersion", "8.8")
            .accept(ContentType.JSON)
            .post("/convert")
            .getBody()
            .asByteArray();

    String converted = new String(form, StandardCharsets.UTF_8);
    assertThat(converted).contains("\"executionPlatformVersion\": \"8.8.0\"");
  }

  @Test
  void convertBatchIncludesFormFiles() throws URISyntaxException, IOException {
    byte[] zip =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("simple.form").toURI()))
            .accept("application/zip")
            .post("/convertBatch")
            .getBody()
            .asByteArray();

    Map<String, String> entries = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry zipEntry;
      while ((zipEntry = zis.getNextEntry()) != null) {
        entries.put(zipEntry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
        zis.closeEntry();
      }
    }

    assertThat(entries).containsOnlyKeys("converted-c8-example.bpmn", "converted-c8-simple.form");
    assertThat(entries.get("converted-c8-simple.form"))
        .contains("\"executionPlatform\": \"Camunda Cloud\"")
        .contains("\"executionPlatformVersion\": \"8.9.0\"");
  }

  @Test
  void convertFormWithBlankPlatformVersionFallsBackToDefault() throws URISyntaxException {
    byte[] form =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("simple.form").toURI()))
            .formParam("platformVersion", " ")
            .accept(ContentType.JSON)
            .post("/convert")
            .getBody()
            .asByteArray();

    String converted = new String(form, StandardCharsets.UTF_8);
    assertThat(converted).contains("\"executionPlatformVersion\": \"8.9.0\"");
  }

  @Test
  void convertBpmnWithBlankPlatformVersionFallsBackToDefault() throws URISyntaxException {
    // a blank platformVersion must behave like an absent one for all file types: the configured
    // default applies instead of failing version parsing in core (e.g. SemanticVersion.parse)
    byte[] bpmn =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .formParam("platformVersion", " ")
            .accept("application/bpmn+xml")
            .post("/convert")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray();

    assertThat(new String(bpmn, StandardCharsets.UTF_8))
        .contains("executionPlatformVersion=\"8.9.0\"");
  }

  @Test
  void convertFormWithInvalidPlatformVersionReturnsBadRequest() throws URISyntaxException {
    RestAssured.given()
        .contentType(ContentType.MULTIPART)
        .multiPart("file", new File(getClass().getClassLoader().getResource("simple.form").toURI()))
        .formParam("platformVersion", "not-a-version")
        .accept(ContentType.JSON)
        .post("/convert")
        .then()
        .statusCode(400);
  }

  @Test
  void convertFormWithInvalidJsonReturnsBadRequest() {
    RestAssured.given()
        .contentType(ContentType.MULTIPART)
        .multiPart("file", "broken.form", "this is not json".getBytes(StandardCharsets.UTF_8))
        .accept(ContentType.JSON)
        .post("/convert")
        .then()
        .statusCode(400);
  }

  @Test
  void convertBatchWithInvalidFormJsonReturnsBadRequest() {
    RestAssured.given()
        .contentType(ContentType.MULTIPART)
        .multiPart("file", "broken.form", "this is not json".getBytes(StandardCharsets.UTF_8))
        .accept("application/zip")
        .post("/convertBatch")
        .then()
        .statusCode(400);
  }

  @Test
  void convertSanitizesFilenameInContentDisposition() throws URISyntaxException, IOException {
    byte[] formBytes =
        Files.readAllBytes(
            new File(getClass().getClassLoader().getResource("simple.form").toURI()).toPath());
    String contentDisposition =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart("file", "../../evil.form", formBytes, "application/json")
            .accept(ContentType.JSON)
            .post("/convert")
            .getHeader("Content-Disposition");

    assertThat(contentDisposition).contains("converted-c8-evil.form");
    assertThat(contentDisposition).doesNotContain("..").doesNotContain("/");
  }

  @Test
  void convertBatchSanitizesZipEntryNames() throws URISyntaxException, IOException {
    byte[] formBytes =
        Files.readAllBytes(
            new File(getClass().getClassLoader().getResource("simple.form").toURI()).toPath());
    byte[] bpmnBytes =
        Files.readAllBytes(
            new File(getClass().getClassLoader().getResource("example.bpmn").toURI()).toPath());
    byte[] zip =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart("file", "../../evil.form", formBytes, "application/json")
            .multiPart("file", "..\\..\\evil.bpmn", bpmnBytes, "application/bpmn+xml")
            .accept("application/zip")
            .post("/convertBatch")
            .getBody()
            .asByteArray();

    List<String> entryNames = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry zipEntry;
      while ((zipEntry = zis.getNextEntry()) != null) {
        entryNames.add(zipEntry.getName());
        zis.closeEntry();
      }
    }

    assertThat(entryNames)
        .containsExactlyInAnyOrder("converted-c8-evil.form", "converted-c8-evil.bpmn");
  }

  @Test
  void convertBatchKeepsDuplicateFileNames() throws URISyntaxException, IOException {
    byte[] formBytes =
        Files.readAllBytes(
            new File(getClass().getClassLoader().getResource("simple.form").toURI()).toPath());
    byte[] bpmnBytes =
        Files.readAllBytes(
            new File(getClass().getClassLoader().getResource("example.bpmn").toURI()).toPath());
    byte[] zip =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart("file", "same.form", formBytes, "application/json")
            .multiPart("file", "same.form", formBytes, "application/json")
            .multiPart("file", "same.bpmn", bpmnBytes, "application/bpmn+xml")
            .multiPart("file", "same.bpmn", bpmnBytes, "application/bpmn+xml")
            .accept("application/zip")
            .post("/convertBatch")
            .getBody()
            .asByteArray();

    List<String> entryNames = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry zipEntry;
      while ((zipEntry = zis.getNextEntry()) != null) {
        entryNames.add(zipEntry.getName());
        zis.closeEntry();
      }
    }

    assertThat(entryNames)
        .containsExactlyInAnyOrder(
            "converted-c8-same.form",
            "converted-c8-same (1).form",
            "converted-c8-same.bpmn",
            "converted-c8-same (1).bpmn");
  }
}
