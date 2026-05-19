/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.visitor.impl.activity.BusinessRuleTaskVisitor;
import io.camunda.migration.diagram.converter.visitor.impl.activity.ScriptTaskVisitor;

final class JobPriorityScope {
  private JobPriorityScope() {}

  /**
   * Whether the source BPMN element is one that the issue accepts as a priority carrier — process
   * plus the job-worker-backed tasks. Business-rule and script tasks are only eligible when they
   * convert to a job worker (i.e. not DMN-backed, not an internal FEEL script).
   */
  static boolean isEligible(DomElementVisitorContext context) {
    return switch (context.getElement().getLocalName()) {
      case "process", "serviceTask", "sendTask" -> true;
      case "businessRuleTask" -> !BusinessRuleTaskVisitor.isDmnImplementation(context);
      case "scriptTask" -> !ScriptTaskVisitor.canBeInternalScript(context);
      default -> false;
    };
  }
}
