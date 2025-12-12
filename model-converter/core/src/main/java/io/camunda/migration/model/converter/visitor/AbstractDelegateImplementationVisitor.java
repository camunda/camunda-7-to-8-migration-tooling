/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractDelegateImplementationVisitor
    extends AbstractSupportedAttributeVisitor {
  public static final Pattern DELEGATE_NAME_EXTRACT = Pattern.compile("[#$]\\{(.*)}");
  private static final Set<String> IGNORE =
      Stream.of("taskListener", "executionListener", "errorEventDefinition")
          .collect(Collectors.toSet());

  // private static final Pattern SIMPLE_EXPRESSION_PATTERN =
  //    Pattern.compile("[#$]\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.([a-zA-Z_][a-zA-Z0-9_]*))?}");

  //
  //  private String extractJobType(String attribute) {
  //    Matcher matcher = DELEGATE_NAME_EXTRACT.matcher(attribute);
  //
  //    if (matcher.matches()) {
  //        // Expression detected (e.g. #{bean.foo})
  //        return matcher.group(1);
  //    } else if (attribute.contains(".")) {
  //        // Likely a fully-qualified class name
  //        int lastDot = attribute.lastIndexOf('.');
  //        // return classname but with lower case first character
  //        return decapitalize(attribute.substring(lastDot + 1));
  //    } else {
  //        return null;
  //    }
  //  }
  //  private static final Pattern EXPRESSION_WRAPPER_PATTERN = Pattern.compile("[#$]\\{(.*)}");

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    if (attribute != null && !attribute.trim().isEmpty()) {
      context.addConversion(
          ServiceTaskConvertible.class,
          serviceTaskConversion ->
              serviceTaskConversion.addZeebeTaskHeader(attributeLocalName(), attribute));
    }
    // option 1: job type should always be blank
    if (context.getProperties().getKeepJobTypeBlank()) {
      return MessageFactory.delegateImplementationNoDefaultJobType(attributeLocalName(), attribute);
    }
    // option 2: job type should always be default
    if (context.getProperties().getAlwaysUseDefaultJobType()) {
      return defaultJobType(context, attribute);
    }
    // option 3: use delegate implementation attribute to form job type
    String jobType = extractJobType(context, attribute);
    // option 3.1: attribute is null or empty -> do not use it
    if (jobType == null || jobType.trim().isEmpty()) {
      return MessageFactory.delegateExpressionAsJobTypeNull(attribute);
    }
    // option 3.2: the extension knows how to build everything
    List<Consumer<ServiceTaskConvertible>> additionalConversions =
        additionalConversions(context, attribute);
    Message message = returnMessage(context, attribute);
    context.addConversion(
        ServiceTaskConvertible.class,
        serviceTaskConversion -> serviceTaskConversion.getZeebeTaskDefinition().setType(jobType));
    additionalConversions.forEach(
        conversion -> context.addConversion(ServiceTaskConvertible.class, conversion));
    return message;
  }

  private Message defaultJobType(DomElementVisitorContext context, final String attribute) {
    context.addConversion(
        ServiceTaskConvertible.class,
        serviceTaskConversion ->
            serviceTaskConversion
                .getZeebeTaskDefinition()
                .setType(context.getProperties().getDefaultJobType()));
    return MessageFactory.delegateImplementation(
        attributeLocalName(),
        context.getElement().getLocalName(),
        attribute,
        context.getProperties().getDefaultJobType());
  }

  protected abstract String extractJobType(
      DomElementVisitorContext context, final String attribute);

  protected abstract List<Consumer<ServiceTaskConvertible>> additionalConversions(
      DomElementVisitorContext context, final String attribute);

  protected abstract Message returnMessage(
      DomElementVisitorContext context, final String attribute);

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    String localName = context.getElement().getLocalName();
    return super.canVisit(context) && !IGNORE.contains(localName);
  }
}
