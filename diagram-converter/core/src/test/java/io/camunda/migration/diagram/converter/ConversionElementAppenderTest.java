/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static io.camunda.migration.diagram.converter.BpmnElementFactory.getDocumentation;
import static io.camunda.migration.diagram.converter.DiagramCheckResult.Severity.*;
import static io.camunda.migration.diagram.converter.NamespaceUri.BPMN;
import static io.camunda.migration.diagram.converter.bpmn.BpmnTestcaseUtils.wrapSnippetInProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckMessage;
import java.util.List;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.junit.jupiter.api.Test;

class ConversionElementAppenderTest {

  @Test
  void shouldAppendOnlyTaskAndWarningMessagesWhenRequested() {
    DomElement element = elementWithDocumentation("Existing documentation.");

    new ConversionElementAppender()
        .appendDocumentation(
            element,
            List.of(
                message(WARNING, "warning message"),
                message(TASK, "task message"),
                message(REVIEW, "review message"),
                message(INFO, "info message")),
            true);

    assertThat(getDocumentation(element).getTextContent())
        .contains("Existing documentation.")
        .contains("- WARNING: warning message")
        .contains("- TASK: task message")
        .doesNotContain("review message")
        .doesNotContain("info message");
  }

  @Test
  void shouldLeaveExistingDocumentationUnchangedWhenNoTaskOrWarningMessagesExist() {
    DomElement element = elementWithDocumentation("Existing documentation.");

    new ConversionElementAppender()
        .appendDocumentation(
            element,
            List.of(message(REVIEW, "review message"), message(INFO, "info message")),
            true);

    assertThat(getDocumentation(element).getTextContent()).isEqualTo("Existing documentation.");
  }

  @Test
  void shouldNotCreateDocumentationWhenFilteringRemovesAllMessages() {
    DomElement element = elementWithoutDocumentation();

    new ConversionElementAppender()
        .appendDocumentation(element, List.of(message(REVIEW, "review message")), true);

    assertThat(element.getChildElementsByNameNs(BPMN, "documentation")).isEmpty();
  }

  private DomElement elementWithDocumentation(String documentation) {
    BpmnModelInstance modelInstance =
        wrapSnippetInProcess(
            """
            <bpmn:serviceTask id="task">
              <bpmn:documentation>%s</bpmn:documentation>
            </bpmn:serviceTask>
            """
                .formatted(documentation));
    return modelInstance.getDocument().getElementById("task");
  }

  private DomElement elementWithoutDocumentation() {
    BpmnModelInstance modelInstance = wrapSnippetInProcess("<bpmn:serviceTask id=\"task\" />");
    return modelInstance.getDocument().getElementById("task");
  }

  private ElementCheckMessage message(DiagramCheckResult.Severity severity, String text) {
    ElementCheckMessage message = new ElementCheckMessage();
    message.setSeverity(severity);
    message.setMessage(text);
    return message;
  }
}
