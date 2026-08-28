/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { describe, expect, it } from "vitest";
import { getPreviewType } from "./modelType";

describe("getPreviewType", () => {
  it.each([
    ["decision.dmn", "dmn"],
    ["decision.dmn11.xml", "dmn"],
    ["decision.DMN", "dmn"],
    ["process.bpmn", "bpmn"],
    ["process.bpmn20.xml", "bpmn"],
    ["customer.form", "form"],
    ["CUSTOMER.FORM", "form"],
    ["unknown.xml", "other"],
    ["unknown.txt", "other"],
  ])("identifies %s as %s", (fileName, expectedType) => {
    expect(getPreviewType(fileName)).toBe(expectedType);
  });

  it("detects BPMN and DMN content in generic XML files", () => {
    expect(
      getPreviewType(
        "process.xml",
        '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />'
      )
    ).toBe("bpmn");
    expect(
      getPreviewType(
        "decision.xml",
        '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" />'
      )
    ).toBe("dmn");
  });

  it("returns other for invalid file names without recognizable model content", () => {
    expect(getPreviewType(undefined)).toBe("other");
  });
});
