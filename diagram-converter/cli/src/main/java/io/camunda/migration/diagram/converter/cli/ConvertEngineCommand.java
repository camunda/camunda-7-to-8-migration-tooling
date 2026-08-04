/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.cli;

import io.camunda.migration.diagram.converter.DiagramType;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.camunda.bpm.model.xml.ModelInstance;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "engine",
    descriptionHeading = "Description: ",
    description = {
      "Converts the diagrams from the given process engine",
      "%nExecute as:",
      "%njava -Dfile.encoding=UTF-8 -jar backend-diagram-converter-cli.jar engine%n"
    },
    mixinStandardHelpOptions = true,
    optionListHeading = "Options:%n",
    parameterListHeading = "Parameter:%n",
    showDefaultValues = true)
public class ConvertEngineCommand extends AbstractConvertCommand {
  private static final String DEFAULT_URL = "http://localhost:8080/engine-rest";

  @Parameters(
      index = "0",
      description = "Fully qualified http(s) address to the process engine REST API",
      defaultValue = DEFAULT_URL)
  String url = DEFAULT_URL;

  @Option(
      names = {"-u", "--username"},
      description = "Username for Basic authentication",
      paramLabel = "<username>")
  String username;

  @Option(
      names = {"-p", "--password"},
      description = "Password for Basic authentication",
      paramLabel = "<password>")
  String password;

  @Option(
      names = {"-t", "--target-directory"},
      description = "The directory to save the .bpmn files",
      paramLabel = "<targetDirectory>",
      defaultValue = ".")
  File targetDirectory = new File(".");

  @Override
  protected File targetDirectory() {
    return targetDirectory;
  }

  @Override
  protected Map<File, ModelInstance> modelInstances() {
    Map<File, ModelInstance> result = new LinkedHashMap<>();
    addModelInstances(getAllLatestBpmnXml(), DiagramType.BPMN, result);
    addModelInstances(getAllLatestDmnXml(), DiagramType.DMN, result);
    return result;
  }

  private void addModelInstances(
      Map<String, Map<String, Set<String>>> diagrams,
      DiagramType diagramType,
      Map<File, ModelInstance> result) {
    diagrams.forEach(
        (resourceName, models) ->
            models.forEach(
                (model, processDefinitionKeys) -> {
                  String resourceFilename = FilenameUtils.getName(resourceName);
                  String filename =
                      models.size() == 1
                          ? resourceFilename
                          : multiModelFilename(
                              resourceFilename, processDefinitionKeys, diagramType);
                  filename = FilenameUtils.getName(filename);
                  filename = safeFilename(filename, diagramType);
                  File outputFile = uniqueOutputFile(filename, diagramType, result);
                  result.put(
                      outputFile,
                      diagramType.readDiagram(new ByteArrayInputStream(model.getBytes())));
                }));
  }

  static String safeFilename(String filename, DiagramType diagramType) {
    if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
      return "diagram" + diagramType.getFileEndings().get(0);
    }
    return filename;
  }

  private String multiModelFilename(
      String resourceFilename, Set<String> processDefinitionKeys, DiagramType diagramType) {
    String fileEnding = diagramEnding(resourceFilename, diagramType);
    if (fileEnding.isEmpty()) {
      return "diagram ("
          + String.join(", ", processDefinitionKeys)
          + ")"
          + diagramType.getFileEndings().get(0);
    }
    String baseName =
        resourceFilename.substring(0, resourceFilename.length() - fileEnding.length());
    return baseName + " (" + String.join(", ", processDefinitionKeys) + ")" + fileEnding;
  }

  private String diagramEnding(String filename, DiagramType diagramType) {
    return diagramType.getFileEndings().stream()
        .filter(filename::endsWith)
        .findFirst()
        .orElseGet(
            () -> {
              String extension = FilenameUtils.getExtension(filename);
              return extension.isEmpty() ? "" : "." + extension;
            });
  }

  private File uniqueOutputFile(
      String filename, DiagramType diagramType, Map<File, ModelInstance> existingFiles) {
    File outputFile = new File(targetDirectory, filename);
    int counter = 0;
    while (existingFiles.containsKey(outputFile)) {
      counter++;
      String fileEnding = diagramEnding(filename, diagramType);
      outputFile =
          new File(
              targetDirectory,
              filename.substring(0, filename.length() - fileEnding.length())
                  + " ("
                  + counter
                  + ")"
                  + fileEnding);
    }
    return outputFile;
  }

  private Map<String, Map<String, Set<String>>> getAllLatestBpmnXml() {
    ProcessEngineClient client = ProcessEngineClient.withEngine(url, username, password);
    return client.getAllLatestProcessDefinitions().stream()
        .collect(
            Collectors.groupingBy(
                ProcessDefinitionDto::getResource,
                Collectors.groupingBy(
                    pd -> client.getBpmnXml(pd.getId()).getBpmn20Xml(),
                    Collectors.mapping(ProcessDefinitionDto::getKey, Collectors.toSet()))));
  }

  private Map<String, Map<String, Set<String>>> getAllLatestDmnXml() {
    ProcessEngineClient client = ProcessEngineClient.withEngine(url, username, password);
    return client.getAllLatestDecisionDefinitions().stream()
        .collect(
            Collectors.groupingBy(
                DecisionDefinitionDto::getResource,
                Collectors.groupingBy(
                    pd -> client.getDmnXml(pd.getId()).getDmnXml(),
                    Collectors.mapping(DecisionDefinitionDto::getKey, Collectors.toSet()))));
  }
}
