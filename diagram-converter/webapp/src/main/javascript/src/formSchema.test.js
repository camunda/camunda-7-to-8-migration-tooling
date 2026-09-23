/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { describe, expect, it } from "vitest";
import { parseFormSchema } from "./formSchema";

describe("parseFormSchema", () => {
  it("parses a form object", () => {
    const schema = { type: "default", components: [] };

    expect(parseFormSchema(JSON.stringify(schema))).toEqual({
      schema,
      error: "",
    });
  });

  it.each([null, undefined, ""])(
    "reports missing content as unavailable",
    (content) => {
      expect(parseFormSchema(content)).toEqual({
        schema: null,
        error: "The form content is unavailable.",
      });
    }
  );

  it.each([
    ["{ invalid", "The form content is not valid JSON."],
    ["[]", "The form content is not a JSON object."],
    [0, "The form content is not a JSON object."],
    [false, "The form content is not a JSON object."],
    ["0", "The form content is not a JSON object."],
  ])("reports invalid content", (content, error) => {
    expect(parseFormSchema(content)).toEqual({ schema: null, error });
  });
});
