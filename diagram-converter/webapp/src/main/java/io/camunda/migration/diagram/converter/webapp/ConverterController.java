/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.webapp;

import io.camunda.migration.diagram.converter.DiagramCheckResult;
import io.camunda.migration.diagram.converter.DiagramConverterResultDTO;
import io.camunda.migration.diagram.converter.DiagramType;
import io.camunda.migration.diagram.converter.excel.ExcelWriter;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.camunda.bpm.model.xml.ModelInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "*")
public class ConverterController {
  private static final Logger LOG = LoggerFactory.getLogger(ConverterController.class);

  private static final String TEXT_CSV = "text/csv";
  private static final String APPLICATION_EXCEL = "application/excel";
  private static final String APPLICATION_MS_EXCEL = "application/vnd.ms-excel";
  private static final String APPLICATION_XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  /**
   * Media type for the flat, machine-readable analysis report: a JSON array with one flat object
   * per finding (same format as the CLI's {@code analysis-results.json}), intended for AI / machine
   * analysis.
   */
  public static final String APPLICATION_ANALYSIS_JSON = "application/vnd.camunda.analysis+json";

  private final DiagramConverterService bpmnConverter;
  private final BuildProperties buildProperties;
  private final ExcelWriter excelWriter;

  @Autowired
  public ConverterController(
      DiagramConverterService bpmnConverter,
      BuildProperties buildProperties,
      ExcelWriter excelWriter) {
    this.bpmnConverter = bpmnConverter;
    this.buildProperties = buildProperties;
    this.excelWriter = excelWriter;
  }

  /**
   * POST a list of BPMN or DMN models for analyzing tasks. Can be returned in various formats: -
   * JSON representation of a {@link List} of {@link DiagramCheckResult}s - Excel file, filled with
   * result data - CSV file containing result data - flat machine-readable JSON report (a JSON array
   * of {@link DiagramConverterResultDTO} entries, one per finding, same format as the CLI's
   * analysis-results.json) for AI / machine analysis, requested with {@link
   * #APPLICATION_ANALYSIS_JSON}
   */
  @PostMapping(
      value = "/check",
      produces = {
        MediaType.APPLICATION_JSON_VALUE,
        APPLICATION_ANALYSIS_JSON,
        TEXT_CSV,
        APPLICATION_EXCEL,
        APPLICATION_MS_EXCEL,
        APPLICATION_XLSX
      },
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<?> check(
      @RequestParam("file") List<MultipartFile> diagramFiles,
      @RequestParam(value = "defaultJobType", required = false) String defaultJobType,
      @RequestParam(value = "platformVersion", required = false) String platformVersion,
      @RequestParam(value = "keepJobTypeBlank", required = false, defaultValue = "false")
          Boolean keepJobTypeBlank,
      @RequestParam(value = "alwaysUseDefaultJobType", required = false, defaultValue = "false")
          Boolean alwaysUseDefaultJobType,
      @RequestParam(
              value = "addDataMigrationExecutionListener",
              required = false,
              defaultValue = "false")
          Boolean addDataMigrationExecutionListener,
      @RequestParam(
              value = "dataMigrationExecutionListenerJobType",
              required = false,
              defaultValue = "migrator")
          String dataMigrationExecutionListenerJobType,
      @RequestHeader(HttpHeaders.ACCEPT) String[] contentType) {

    ArrayList<DiagramCheckResult> resultList = new ArrayList<DiagramCheckResult>();

    // Check all files
    for (Iterator diagramFilesIterator = diagramFiles.iterator();
        diagramFilesIterator.hasNext(); ) {
      MultipartFile diagramFile = (MultipartFile) diagramFilesIterator.next();
      DiagramType diagramType = determineDiagramType(diagramFile);

      try (InputStream in = diagramFile.getInputStream()) {
        ModelInstance modelInstance = diagramType.readDiagram(in);

        DiagramCheckResult diagramCheckResult =
            bpmnConverter.check(
                diagramFile.getOriginalFilename(),
                modelInstance,
                defaultJobType,
                platformVersion,
                keepJobTypeBlank,
                alwaysUseDefaultJobType,
                addDataMigrationExecutionListener,
                dataMigrationExecutionListenerJobType);
        resultList.add(diagramCheckResult);
      } catch (IOException e) {
        LOG.error("Error while reading input stream of diagram file", e);
        return ResponseEntity.badRequest().body(e.getMessage());
      }
    }

    // return response depending on the requested format
    if (analysisJsonRequested(contentType)) { // flat machine-readable JSON report

      StringWriter sw = new StringWriter();
      bpmnConverter.writeJsonFile(resultList, sw);
      Resource file = new ByteArrayResource(sw.toString().getBytes(StandardCharsets.UTF_8));
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis-results.json\"")
          .contentType(MediaType.parseMediaType(APPLICATION_ANALYSIS_JSON + ";charset=UTF-8"))
          .body(file);

    } else if (jsonRequested(contentType)) { // JSON
      return ResponseEntity.ok(resultList);

    } else if (excelRequested(contentType)) { // EXCEL

      List<DiagramConverterResultDTO> data = bpmnConverter.createLineItemDTOList(resultList);

      ByteArrayOutputStream os = new ByteArrayOutputStream();
      excelWriter.writeResultsToExcel(data, os);
      Resource file = new ByteArrayResource(os.toByteArray());

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"migrationAnalyzer.xlsx\"")
          .header(
              HttpHeaders.CONTENT_TYPE,
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(file);

    } else if (csvRequested(contentType)) { // CSV

      StringWriter sw = new StringWriter();
      bpmnConverter.writeCsvFile(resultList, sw);
      Resource file = new ByteArrayResource(sw.toString().getBytes());
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"migrationAnalyzer.csv\"")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(file);

    } else {
      String errorMessage = "Invalid content type '" + String.join("', '", contentType) + "'";
      LOG.error("{}", errorMessage);
      return ResponseEntity.badRequest().body(errorMessage);
    }
  }

  private boolean csvRequested(String[] contentType) {
    return bestMatch(contentType)
        .map(mediaType -> mediaType.equalsTypeAndSubtype(MediaType.valueOf(TEXT_CSV)))
        .orElse(false);
  }

  private boolean analysisJsonRequested(String[] contentType) {
    return bestMatch(contentType)
        .map(
            mediaType ->
                mediaType.equalsTypeAndSubtype(MediaType.parseMediaType(APPLICATION_ANALYSIS_JSON)))
        .orElse(false);
  }

  private boolean jsonRequested(String[] contentType) {
    // JSON is the default representation: an absent or wildcard-best Accept header counts as JSON
    return contentType == null
        || contentType.length == 0
        || bestMatch(contentType)
            .map(
                mediaType ->
                    mediaType.isWildcardType()
                        || mediaType.isWildcardSubtype()
                        || mediaType.equalsTypeAndSubtype(MediaType.APPLICATION_JSON))
            .orElse(false);
  }

  private boolean excelRequested(String[] contentType) {
    return bestMatch(contentType)
        .map(
            mediaType ->
                mediaType.equalsTypeAndSubtype(MediaType.valueOf(APPLICATION_EXCEL))
                    || mediaType.equalsTypeAndSubtype(MediaType.valueOf(APPLICATION_MS_EXCEL))
                    || mediaType.equalsTypeAndSubtype(MediaType.valueOf(APPLICATION_XLSX)))
        .orElse(false);
  }

  private Optional<MediaType> bestMatch(String[] contentType) {
    if (contentType == null || contentType.length == 0) {
      return Optional.empty();
    }
    List<MediaType> supported =
        List.of(
            MediaType.APPLICATION_JSON,
            MediaType.parseMediaType(APPLICATION_ANALYSIS_JSON),
            MediaType.valueOf(TEXT_CSV),
            MediaType.valueOf(APPLICATION_EXCEL),
            MediaType.valueOf(APPLICATION_MS_EXCEL),
            MediaType.valueOf(APPLICATION_XLSX));
    List<MediaType> mediaTypes =
        Arrays.stream(contentType)
            .map(MediaType::parseMediaType)
            .filter(
                mediaType ->
                    mediaType.isWildcardType()
                        || mediaType.isWildcardSubtype()
                        || supported.stream().anyMatch(s -> s.equalsTypeAndSubtype(mediaType)))
            .collect(Collectors.toList());
    if (mediaTypes.isEmpty()) {
      return Optional.empty();
    }
    // quality first (q= parameter), then specificity (fewer wildcards), matching Spring's former
    // sortBySpecificityAndQuality semantics
    mediaTypes.sort(
        Comparator.comparingDouble(MediaType::getQualityValue)
            .reversed()
            .thenComparing(MediaType::isWildcardType)
            .thenComparing(MediaType::isWildcardSubtype));
    return Optional.of(mediaTypes.get(0));
  }

  /**
   * POST method to actually convert a BPMN or DMN model.
   *
   * @throws InterruptedException
   */
  @PostMapping(
      value = "/convert",
      produces = {"application/bpmn+xml", "application/dmn+xml"},
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<?> getFile(
      @RequestParam("file") MultipartFile diagramFile,
      @RequestParam(value = "appendDocumentation", required = false, defaultValue = "false")
          Boolean appendDocumentation,
      @RequestParam(value = "defaultJobType", required = false) String defaultJobType,
      @RequestParam(value = "platformVersion", required = false) String platformVersion,
      @RequestParam(value = "keepJobTypeBlank", required = false, defaultValue = "false")
          Boolean keepJobTypeBlank,
      @RequestParam(value = "alwaysUseDefaultJobType", required = false, defaultValue = "false")
          Boolean alwaysUseDefaultJobType,
      @RequestParam(
              value = "addDataMigrationExecutionListener",
              required = false,
              defaultValue = "false")
          Boolean addDataMigrationExecutionListener,
      @RequestParam(
              value = "dataMigrationExecutionListenerJobType",
              required = false,
              defaultValue = "migrator")
          String dataMigrationExecutionListenerJobType) {

    DiagramType diagramType = determineDiagramType(diagramFile);
    try (InputStream in = diagramFile.getInputStream()) {
      ModelInstance modelInstance = diagramType.readDiagram(in);
      bpmnConverter.convert(
          modelInstance,
          appendDocumentation,
          defaultJobType,
          platformVersion,
          keepJobTypeBlank,
          alwaysUseDefaultJobType,
          addDataMigrationExecutionListener,
          dataMigrationExecutionListenerJobType);
      String xml = bpmnConverter.printXml(modelInstance.getDocument(), true);
      Resource file = new ByteArrayResource(xml.getBytes());
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"converted-c8-" + diagramFile.getOriginalFilename() + "\"")
          .header(HttpHeaders.CONTENT_TYPE, diagramType.getContentType())
          .body(file);
    } catch (IOException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      LOG.error("Error while converting resources", e);
      return ResponseEntity.internalServerError().body(e.getMessage());
    }
  }

  /**
   * POST method to convert a list of BPMN or DMN models in one go. Returns a ZIP file with all the
   * contents
   */
  @PostMapping(
      value = "/convertBatch",
      produces = {"application/zip"},
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<?> convertBatch(
      @RequestParam("file") List<MultipartFile> diagramFiles,
      @RequestParam(value = "appendDocumentation", required = false, defaultValue = "false")
          Boolean appendDocumentation,
      @RequestParam(value = "defaultJobType", required = false) String defaultJobType,
      @RequestParam(value = "platformVersion", required = false) String platformVersion,
      @RequestParam(value = "keepJobTypeBlank", required = false, defaultValue = "false")
          Boolean keepJobTypeBlank,
      @RequestParam(value = "alwaysUseDefaultJobType", required = false, defaultValue = "false")
          Boolean alwaysUseDefaultJobType,
      @RequestParam(
              value = "addDataMigrationExecutionListener",
              required = false,
              defaultValue = "false")
          Boolean addDataMigrationExecutionListener,
      @RequestParam(
              value = "dataMigrationExecutionListenerJobType",
              required = false,
              defaultValue = "migrator")
          String dataMigrationExecutionListenerJobType) {

    HashMap<String, Resource> resultList = new HashMap<String, Resource>();

    // Check all files
    for (Iterator diagramFilesIterator = diagramFiles.iterator();
        diagramFilesIterator.hasNext(); ) {
      MultipartFile diagramFile = (MultipartFile) diagramFilesIterator.next();

      DiagramType diagramType = determineDiagramType(diagramFile);
      try (InputStream in = diagramFile.getInputStream()) {

        ModelInstance modelInstance = diagramType.readDiagram(in);
        bpmnConverter.convert(
            modelInstance,
            appendDocumentation,
            defaultJobType,
            platformVersion,
            keepJobTypeBlank,
            alwaysUseDefaultJobType,
            addDataMigrationExecutionListener,
            dataMigrationExecutionListenerJobType);
        String xml = bpmnConverter.printXml(modelInstance.getDocument(), true);
        Resource file = new ByteArrayResource(xml.getBytes());
        resultList.put("converted-c8-" + diagramFile.getOriginalFilename(), file);

      } catch (IOException e) {
        LOG.error("IO Error while converting resources in batch", e);
        return ResponseEntity.badRequest().body(e.getMessage());
      } catch (Exception e) {
        LOG.error("Error while converting resources in batch", e);
        return ResponseEntity.internalServerError().body(e.getMessage());
      }
    }

    // Creating byteArray stream, make it bufferable and passing this buffer to ZipOutputStream
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream);

    try (ZipOutputStream zipOutputStream = new ZipOutputStream(bufferedOutputStream)) {
      for (Entry<String, Resource> file : resultList.entrySet()) {
        zipOutputStream.putNextEntry(new ZipEntry(file.getKey()));
        zipOutputStream.write(file.getValue().getContentAsByteArray());
        zipOutputStream.closeEntry();
      }
    } catch (IOException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted-diagrams.zip\"")
        .header(HttpHeaders.CONTENT_TYPE, "application/zip")
        .body(byteArrayOutputStream.toByteArray());
  }

  /**
   * GET method to retrieve the current version of the diagram converter tool
   *
   * @return
   */
  @GetMapping(value = "/version", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> getVersion() {
    String implementationVersion = buildProperties.getVersion();
    LOG.debug("Version: {}", implementationVersion);
    return ResponseEntity.ok().body(implementationVersion);
  }

  private DiagramType determineDiagramType(MultipartFile file) {
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null) {
      throw new IllegalArgumentException("No file provided");
    }
    return DiagramType.fromFileName(originalFilename);
  }
}
