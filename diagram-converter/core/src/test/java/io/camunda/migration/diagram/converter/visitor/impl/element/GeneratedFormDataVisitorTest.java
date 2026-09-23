/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.element;

import static io.camunda.migration.diagram.converter.NamespaceUri.BPMN;
import static io.camunda.migration.diagram.converter.NamespaceUri.CONVERSION;
import static io.camunda.migration.diagram.converter.bpmn.BpmnTestcaseUtils.wrapSnippetInProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.ConverterPropertiesFactory;
import io.camunda.migration.diagram.converter.DiagramCheckResult;
import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import io.camunda.migration.diagram.converter.DiagramConverterFactory;
import java.util.stream.Stream;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GeneratedFormDataVisitorTest {

  @ParameterizedTest
  @MethodSource("generatedForms")
  void shouldReportOneManualTaskPerGeneratedFormOwner(
      String ownerId, String generatedFormElement, String bpmn) {
    BpmnModelInstance modelInstance = wrapSnippetInProcess(bpmn);

    DiagramCheckResult result =
        DiagramConverterFactory.getInstance()
            .get()
            .check(
                "generated-form.bpmn",
                modelInstance,
                ConverterPropertiesFactory.getInstance().get());

    assertThat(result.getResult(ownerId).getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-data");
              assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
              assertThat(message.getMessage())
                  .isEqualTo(
                      "Element '%s' cannot be transformed. Migrate this Generated Task Form to a Camunda 8 form and link it manually."
                          .formatted(generatedFormElement));
              assertThat(message.getLink())
                  .isEqualTo(
                      "https://docs.camunda.io/docs/components/modeler/forms/utilizing-forms/");
            });

    DomElement owner = modelInstance.getDocument().getElementById(ownerId);
    assertThat(owner.getChildElementsByNameNs(BPMN, "extensionElements"))
        .singleElement()
        .satisfies(
            extensionElements ->
                assertThat(extensionElements.getChildElementsByNameNs(CONVERSION, "message"))
                    .singleElement()
                    .satisfies(
                        message -> {
                          assertThat(message.getAttribute("severity")).isEqualTo("TASK");
                          assertThat(message.getTextContent())
                              .isEqualTo(
                                  "Element '%s' cannot be transformed. Migrate this Generated Task Form to a Camunda 8 form and link it manually."
                                      .formatted(generatedFormElement));
                        }));
  }

  private static Stream<Arguments> generatedForms() {
    return Stream.of(
        Arguments.of(
            "FormDataTask",
            "formData",
            """
            <bpmn:userTask id="FormDataTask">
              <bpmn:extensionElements>
                <camunda:formData>
                  <camunda:formField id="name" type="string">
                    <camunda:validation>
                      <camunda:constraint name="required"/>
                    </camunda:validation>
                  </camunda:formField>
                  <camunda:formField id="approved" type="boolean"/>
                </camunda:formData>
              </bpmn:extensionElements>
            </bpmn:userTask>
            """),
        Arguments.of(
            "FormPropertyTask",
            "formProperty",
            """
            <bpmn:userTask id="FormPropertyTask">
              <bpmn:extensionElements>
                <camunda:formProperty id="name" type="string"/>
                <camunda:formProperty id="approved" type="boolean"/>
              </bpmn:extensionElements>
            </bpmn:userTask>
            """),
        Arguments.of(
            "FormDataStart",
            "formData",
            """
            <bpmn:startEvent id="FormDataStart">
              <bpmn:extensionElements>
                <camunda:formData>
                  <camunda:formField id="name" type="string"/>
                </camunda:formData>
              </bpmn:extensionElements>
            </bpmn:startEvent>
            """),
        Arguments.of(
            "FormPropertyStart",
            "formProperty",
            """
            <bpmn:startEvent id="FormPropertyStart">
              <bpmn:extensionElements>
                <camunda:formProperty id="name" type="string"/>
                <camunda:formProperty id="approved" type="boolean"/>
              </bpmn:extensionElements>
            </bpmn:startEvent>
            """),
        Arguments.of(
            "MixedFormTask",
            "formData",
            """
            <bpmn:userTask id="MixedFormTask">
              <bpmn:extensionElements>
                <camunda:formData>
                  <camunda:formField id="name" type="string"/>
                </camunda:formData>
                <camunda:formProperty id="legacy" type="string"/>
              </bpmn:extensionElements>
            </bpmn:userTask>
            """),
        Arguments.of(
            "DuplicateFormDataTask",
            "formData",
            """
            <bpmn:userTask id="DuplicateFormDataTask">
              <bpmn:extensionElements>
                <camunda:formData>
                  <camunda:formField id="name" type="string"/>
                </camunda:formData>
                <camunda:formData>
                  <camunda:formField id="approved" type="boolean"/>
                </camunda:formData>
              </bpmn:extensionElements>
            </bpmn:userTask>
            """));
  }
}
