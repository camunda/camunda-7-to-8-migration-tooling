/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.*;

import org.camunda.bpm.model.dmn.impl.DmnModelConstants;

public interface NamespaceUri {
  String ZEEBE = "http://camunda.org/schema/zeebe/1.0";
  String MODELER = "http://camunda.org/schema/modeler/1.0";
  String CAMUNDA = CAMUNDA_NS;
  String CAMUNDA_DMN = DmnModelConstants.CAMUNDA_NS;
  String BPMN = BPMN20_NS;
  String XSI = "http://www.w3.org/2001/XMLSchema-instance";
  String CONVERSION = "http://camunda.org/schema/conversion/1.0";
  String DMN_15 = DMN15_NS;
  String DMN_11 = DMN11_NS;
  String DMN_12 = DMN12_NS;
  String DMN_13 = DMN13_NS;
  String DMN_14 = DMN14_NS;
  String[] DMN = {DMN_15, DMN_11, DMN_12, DMN_13, DMN_14};
}
