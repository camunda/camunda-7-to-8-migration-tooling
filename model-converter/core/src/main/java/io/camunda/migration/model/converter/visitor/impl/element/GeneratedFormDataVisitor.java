/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.element;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.visitor.AbstractCamundaElementVisitor;

public abstract class GeneratedFormDataVisitor extends AbstractCamundaElementVisitor {

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return false;
  }

  @Override
  protected Message visitCamundaElement(DomElementVisitorContext context) {
    return MessageFactory.generatedFormData();
  }

  public static class FormDataVisitor extends GeneratedFormDataVisitor {

    @Override
    public String localName() {
      return "formData";
    }

    @Override
    protected Message visitCamundaElement(DomElementVisitorContext context) {
      return MessageFactory.formData(context.getElement().getLocalName());
    }
  }

  public static class FormFieldVisitor extends GeneratedFormDataVisitor {
    @Override
    public String localName() {
      return "formField";
    }
  }

  public static class ValidationVisitor extends GeneratedFormDataVisitor {
    @Override
    public String localName() {
      return "validation";
    }
  }

  public static class ConstraintVisitor extends GeneratedFormDataVisitor {
    @Override
    public String localName() {
      return "constraint";
    }
  }

  public static class FormPropertyVisitor extends GeneratedFormDataVisitor {
    @Override
    public String localName() {
      return "formProperty";
    }
  }
}
