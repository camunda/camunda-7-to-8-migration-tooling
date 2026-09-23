/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { describe, expect, it } from "vitest";
import { getPlatformVersionAriaLabel } from "./platformVersions";

describe("platform version accessibility labels", () => {
  it("omits an absent optional hint from the accessible label", () => {
    expect(getPlatformVersionAriaLabel({ label: "8.11" })).toBe("8.11");
  });
});
