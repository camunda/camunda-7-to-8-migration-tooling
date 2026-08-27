/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
const DMN_FILE_ENDINGS = [".dmn", ".dmn11.xml"];

export function getPreviewType(fileName) {
  const normalizedFileName = typeof fileName === "string" ? fileName.toLowerCase() : "";

  if (normalizedFileName.endsWith(".form")) {
    return "form";
  }

  if (DMN_FILE_ENDINGS.some((ending) => normalizedFileName.endsWith(ending))) {
    return "dmn";
  }

  return "bpmn";
}
