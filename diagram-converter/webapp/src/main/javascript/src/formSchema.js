/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export function parseFormSchema(content) {
  if (!content) {
    return {
      schema: null,
      error: "Unable to render this form because its content is unavailable.",
    };
  }

  let schema;
  try {
    schema = JSON.parse(content);
  } catch (error) {
    if (!(error instanceof SyntaxError)) {
      throw error;
    }
    return {
      schema: null,
      error: "Unable to render this form because its content is not valid JSON.",
    };
  }

  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    return {
      schema: null,
      error: "Unable to render this form because its content is not a JSON object.",
    };
  }

  return { schema, error: "" };
}
