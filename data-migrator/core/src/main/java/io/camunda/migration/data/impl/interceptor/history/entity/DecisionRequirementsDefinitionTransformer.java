/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_LEGACY_PREFIX;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Set;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(9)
@Component
public class DecisionRequirementsDefinitionTransformer implements EntityInterceptor {

  public static final String REQUIRED_DECISION = "requiredDecision";
  public static final String DECISION = "decision";
  public static final String DI_DMN_ELEMENT_REF = "dmnElementRef";
  public static final String DI_DMN_SHAPE = "DMNShape";
  public static final String ID = "id";
  public static final String HREF = "href";
  public static final String HASH = "#";

  @Autowired
  protected C7Client c7Client;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DecisionRequirementsDefinition.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    DecisionRequirementsDefinition c7DecisionRequirements = (DecisionRequirementsDefinition) context.getC7Entity();
    if (c7DecisionRequirements == null) {
      // DMNs consisting of just one decision do not have a DRD in C7
      return;
    }
    DecisionRequirementsDbModel.Builder builder =
        (DecisionRequirementsDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 DecisionRequirementsDbModel.Builder is null in context");
    }

    String deploymentId = c7DecisionRequirements.getDeploymentId();
    String resourceName = c7DecisionRequirements.getResourceName();

    InputStream resourceAsStream = c7Client.getResourceAsStream(deploymentId, resourceName);
    String dmnXml = prefixDecisionIdsInDmn(resourceAsStream);

    builder.decisionRequirementsKey(getNextKey())
        .decisionRequirementsId(prefixDefinitionId(c7DecisionRequirements.getKey()))
        .name(c7DecisionRequirements.getName())
        .resourceName(resourceName)
        .version(c7DecisionRequirements.getVersion())
        .xml(dmnXml)
        .tenantId(getTenantId(c7DecisionRequirements.getTenantId()));
  }

  /**
   * Ensures that the Operate UI matches DRG evaluation badges correctly with the correct decision nodes.
   * Prefix decision IDs with "c7-legacy-" and update requiredDecision href references
   * so that decision IDs in the migrated DMN are properly prefixed.
   *
   * @param resourceAsStream the DMN model to modify
   * @return the modified DMN XML as a string
   */
  protected String prefixDecisionIdsInDmn(InputStream resourceAsStream) {
    DmnModelInstance dmnModelInstance = Dmn.readModelFromStream(resourceAsStream);

    DomDocument document = dmnModelInstance.getDocument();
    DomElement rootElement = document.getRootElement();

    // Prefix all decision id attributes with "c7-legacy-"
    prefixDecisionIds(rootElement);

    // Update requiredDecision href attributes to reference the prefixed IDs
    updateRequiredDecisionHrefs(rootElement);

    // Update dmnElementRef attributes in DMNDI section to reference the prefixed IDs
    updateDmnElementRefs(rootElement);

    // Convert the modified DOM back to XML string
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Dmn.writeModelToStream(outputStream, dmnModelInstance);
    return outputStream.toString(Charset.defaultCharset());
  }

  /**
   * Recursively find and prefix all decision id attributes with "c7-legacy-".
   */
  protected void prefixDecisionIds(DomElement element) {
    // Check if this is a decision element
    if (DECISION.equals(element.getLocalName())) {
      String id = element.getAttribute(ID);
      if (id != null && !id.isEmpty() && !id.startsWith(C7_LEGACY_PREFIX)) {
        element.setAttribute(ID, prefixDefinitionId(id));
      }
    }

    // Recursively process child elements
    for (DomElement child : element.getChildElements()) {
      prefixDecisionIds(child);
    }
  }

  /**
   * Recursively find and update all requiredDecision href attributes to reference prefixed IDs.
   */
  protected void updateRequiredDecisionHrefs(DomElement element) {
    // Check if this is a requiredDecision element
    if (REQUIRED_DECISION.equals(element.getLocalName())) {
      String href = element.getAttribute(HREF);
      if (href != null && !href.isEmpty()) {
        // href typically looks like "#decisionId", so we need to preserve the "#" prefix
        if (href.startsWith(HASH) && !href.startsWith(HASH + C7_LEGACY_PREFIX)) {
          element.setAttribute(HREF, HASH + prefixDefinitionId(href.substring(1)));
        }
      }
    }

    // Recursively process child elements
    for (DomElement child : element.getChildElements()) {
      updateRequiredDecisionHrefs(child);
    }
  }

  /**
   * Recursively find and update all dmnElementRef attributes in DMNShape elements to reference prefixed IDs.
   * Note: Only DMNShape elements are processed, not DMNEdge elements.
   */
  protected void updateDmnElementRefs(DomElement element) {
    // Only process dmnElementRef for DMNShape elements, not DMNEdge
    if (DI_DMN_SHAPE.equals(element.getLocalName())) {
      String dmnElementRef = element.getAttribute(DI_DMN_ELEMENT_REF);
      if (dmnElementRef != null && !dmnElementRef.isEmpty() && !dmnElementRef.startsWith(C7_LEGACY_PREFIX)) {
        // dmnElementRef directly references the decision ID without "#" prefix
        element.setAttribute(DI_DMN_ELEMENT_REF, prefixDefinitionId(dmnElementRef));
      }
    }

    // Recursively process child elements
    for (DomElement child : element.getChildElements()) {
      updateDmnElementRefs(child);
    }
  }

}
