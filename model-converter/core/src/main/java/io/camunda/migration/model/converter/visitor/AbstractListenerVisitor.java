/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.NamespaceUri;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor.ListenerImplementation.ClassImplementation;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor.ListenerImplementation.DelegateExpressionImplementation;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor.ListenerImplementation.ExpressionImplementation;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor.ListenerImplementation.NullImplementation;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor.ListenerImplementation.ScriptImplementation;

public abstract class AbstractListenerVisitor extends AbstractCamundaElementVisitor {

  @Override
  protected final Message visitCamundaElement(DomElementVisitorContext context) {
    ListenerImplementation implementation = findListenerImplementation(context);
    String event = findEventName(context);
    return visitListener(context, event, implementation);
  }

  protected String findEventName(DomElementVisitorContext context) {
    return context.getElement().getAttribute(NamespaceUri.CAMUNDA, "event");
  }

  protected abstract Message visitListener(
      DomElementVisitorContext context, String event, ListenerImplementation implementation);

  private ListenerImplementation findListenerImplementation(DomElementVisitorContext context) {
    String listenerImplementation = context.getElement().getAttribute("delegateExpression");
    if (listenerImplementation != null) {
      return new DelegateExpressionImplementation(listenerImplementation);
    }

    listenerImplementation = context.getElement().getAttribute("class");
    if (listenerImplementation != null) {
      return new ClassImplementation(listenerImplementation);
    }
    listenerImplementation = context.getElement().getAttribute("expression");
    if (listenerImplementation != null) {
      return new ExpressionImplementation(listenerImplementation);
    }
    if (context.getElement().getChildElementsByNameNs(NamespaceUri.CAMUNDA, "script") != null
        && !context
            .getElement()
            .getChildElementsByNameNs(NamespaceUri.CAMUNDA, "script")
            .isEmpty()) {
      listenerImplementation =
          context
              .getElement()
              .getChildElementsByNameNs(NamespaceUri.CAMUNDA, "script")
              .get(0)
              .getAttribute("scriptFormat");
      return new ScriptImplementation(listenerImplementation);
    }
    return new NullImplementation();
  }

  public sealed interface ListenerImplementation {
    String implementation();

    static String type(ListenerImplementation implementation) {
      if (implementation == null) {
        return null;
      } else if (implementation instanceof NullImplementation) {
        return "null";
      } else if (implementation instanceof DelegateExpressionImplementation) {
        return "delegateExpression";
      } else if (implementation instanceof ClassImplementation) {
        return "class";
      } else if (implementation instanceof ExpressionImplementation) {
        return "expression";
      } else if (implementation instanceof ScriptImplementation) {
        return "script";
      }
      throw new IllegalArgumentException("Unsupported implementation: " + implementation);
    }

    record DelegateExpressionImplementation(String implementation)
        implements ListenerImplementation {}

    record ClassImplementation(String implementation) implements ListenerImplementation {}

    record ExpressionImplementation(String implementation) implements ListenerImplementation {}

    record ScriptImplementation(String implementation) implements ListenerImplementation {}

    record NullImplementation() implements ListenerImplementation {
      @Override
      public String implementation() {
        return null;
      }
    }
  }
}
