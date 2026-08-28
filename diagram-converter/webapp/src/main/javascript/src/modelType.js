/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
const DMN_FILE_ENDINGS = [".dmn", ".dmn11.xml"];
const BPMN_FILE_ENDINGS = [".bpmn", ".bpmn20.xml"];
const DMN_NAMESPACE = "omg.org/spec/DMN";
const BPMN_NAMESPACE = "omg.org/spec/BPMN";

export function getPreviewType(fileName, modelXml) {
  const normalizedFileName = typeof fileName === "string" ? fileName.toLowerCase() : "";

  if (normalizedFileName.endsWith(".form")) {
    return "form";
  }

  if (DMN_FILE_ENDINGS.some((ending) => normalizedFileName.endsWith(ending))) {
    return "dmn";
  }

  if (
    BPMN_FILE_ENDINGS.some((ending) => normalizedFileName.endsWith(ending)) ||
    (typeof modelXml === "string" && modelXml.includes(BPMN_NAMESPACE))
  ) {
    return "bpmn";
  }

  if (typeof modelXml === "string" && modelXml.includes(DMN_NAMESPACE)) {
    return "dmn";
  }

  return "other";
}
