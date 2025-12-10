/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.webapp;

import io.camunda.migration.model.converter.ConverterPropertiesFactory;
import io.camunda.migration.model.converter.DefaultConverterProperties;
import io.camunda.migration.model.converter.DiagramCheckResult;
import io.camunda.migration.model.converter.DiagramConverter;
import io.camunda.migration.model.converter.DiagramConverterResultDTO;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DiagramConverterService {
  private final DiagramConverter diagramConverter;

  @Autowired
  public DiagramConverterService(DiagramConverter diagramConverter) {
    this.diagramConverter = diagramConverter;
  }

  public void convert(
      ModelInstance modelInstance,
      boolean appendDocumentation,
      String defaultJobType,
      String platformVersion,
      Boolean keepJobTypeBlank,
      Boolean alwaysUseDefaultJobType,
      Boolean addDataMigrationExecutionListener,
      String dataMigrationExecutionListenerJobType) {
    DefaultConverterProperties adaptedProperties = new DefaultConverterProperties();
    adaptedProperties.setDefaultJobType(defaultJobType);
    adaptedProperties.setPlatformVersion(platformVersion);
    adaptedProperties.setKeepJobTypeBlank(keepJobTypeBlank);
    adaptedProperties.setAlwaysUseDefaultJobType(alwaysUseDefaultJobType);
    adaptedProperties.setAddDataMigrationExecutionListener(addDataMigrationExecutionListener);
    adaptedProperties.setDataMigrationExecutionListenerJobType(
        dataMigrationExecutionListenerJobType);
    adaptedProperties.setAppendDocumentation(appendDocumentation);
    diagramConverter.convert(
        modelInstance, ConverterPropertiesFactory.getInstance().merge(adaptedProperties));
  }

  public DiagramCheckResult check(
      String filename,
      ModelInstance modelInstance,
      String defaultJobType,
      String platformVersion,
      Boolean keepJobTypeBlank,
      Boolean alwaysUseDefaultJobType,
      Boolean addDataMigrationExecutionListener,
      String dataMigrationExecutionListenerJobType) {
    DefaultConverterProperties adaptedProperties = new DefaultConverterProperties();
    adaptedProperties.setDefaultJobType(defaultJobType);
    adaptedProperties.setPlatformVersion(platformVersion);
    adaptedProperties.setKeepJobTypeBlank(keepJobTypeBlank);
    adaptedProperties.setAlwaysUseDefaultJobType(alwaysUseDefaultJobType);
    adaptedProperties.setAddDataMigrationExecutionListener(addDataMigrationExecutionListener);
    adaptedProperties.setDataMigrationExecutionListenerJobType(
        dataMigrationExecutionListenerJobType);
    return diagramConverter.check(
        filename, modelInstance, ConverterPropertiesFactory.getInstance().merge(adaptedProperties));
  }

  public String printXml(DomDocument document, boolean prettyPrint) {
    try (StringWriter sw = new StringWriter()) {
      diagramConverter.printXml(document, prettyPrint, sw);
      return sw.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void writeCsvFile(List<DiagramCheckResult> results, Writer writer) {
    diagramConverter.writeCsvFile(results, writer);
  }

  public List<DiagramConverterResultDTO> createLineItemDTOList(List<DiagramCheckResult> results) {
    return diagramConverter.createLineItemDTOList(results);
  }
}
