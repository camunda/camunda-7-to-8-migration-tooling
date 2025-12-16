/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.example.extendedConverter;

import static io.camunda.migration.diagram.converter.NamespaceUri.*;
import static org.assertj.core.api.Assertions.*;

import io.camunda.migration.diagram.converter.ConverterPropertiesFactory;
import io.camunda.migration.diagram.converter.DiagramConverter;
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import io.camunda.migration.diagram.converter.DomElementVisitorFactory;
import io.camunda.migration.diagram.converter.visitor.DomElementVisitor;
import java.io.StringWriter;
import java.util.List;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.junit.jupiter.api.Test;

public class ExtendedConverterTest {
  private static BpmnModelInstance loadModelInstance(String bpmnFile) {
    return Bpmn.readModelFromStream(
        ExtendedConverterTest.class.getClassLoader().getResourceAsStream(bpmnFile));
  }

  @Test
  void shouldLoadCustomDomElementVisitor() {
    List<DomElementVisitor> domElementVisitors = DomElementVisitorFactory.getInstance().get();
    assertThat(domElementVisitors).hasAtLeastOneElementOfType(CustomDomElementVisitor.class);
  }

  @Test
  void shouldAddPropertiesToGateway() {
    DiagramConverter converter = DiagramConverterFactory.getInstance().get();
    BpmnModelInstance modelInstance = loadModelInstance("example-model.bpmn");
    converter.convert(modelInstance, ConverterPropertiesFactory.getInstance().get());
    StringWriter writer = new StringWriter();
    converter.printXml(modelInstance.getDocument(), true, writer);
    System.out.println(writer);
  }

  @Test
  void shouldSetCustomJobType() {
    DiagramConverter converter = DiagramConverterFactory.getInstance().get();
    BpmnModelInstance modelInstance = loadModelInstance("ExternalTaskWorker_Example.bpmn");
    converter.convert(modelInstance, ConverterPropertiesFactory.getInstance().get());
    DomElement extensionElements =
        modelInstance
            .getDocument()
            .getElementById("Activity_1qqj67q")
            .getChildElementsByNameNs(BPMN, "extensionElements")
            .get(0);
    DomElement header =
        extensionElements
            .getChildElementsByNameNs(ZEEBE, "taskHeaders")
            .get(0)
            .getChildElementsByNameNs(ZEEBE, "header")
            .get(0);
    String headerKey = header.getAttribute(ZEEBE, "key");
    String headerValue = header.getAttribute(ZEEBE, "value");
    String jobType =
        extensionElements
            .getChildElementsByNameNs(ZEEBE, "taskDefinition")
            .get(0)
            .getAttribute(ZEEBE, "type");
    assertThat(jobType).isEqualTo("GenericWorker");
    assertThat(headerKey).isEqualTo("topic");
    assertThat(headerValue).isEqualTo("TestTopic");
    StringWriter writer = new StringWriter();
    converter.printXml(modelInstance.getDocument(), true, writer);
    System.out.println(writer);
  }
}
