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

  it.each([
    ["", "Unable to render this form because its content is unavailable."],
    [
      "{ invalid",
      "Unable to render this form because its content is not valid JSON.",
    ],
    [
      "[]",
      "Unable to render this form because its content is not a JSON object.",
    ],
  ])("reports invalid content", (content, error) => {
    expect(parseFormSchema(content)).toEqual({ schema: null, error });
  });
});
