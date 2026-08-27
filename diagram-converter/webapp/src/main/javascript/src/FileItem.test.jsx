/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FileItem from "./FileItem";

vi.mock("@camunda/design-system", () => ({
  Tooltip: ({ children }) => children,
  TooltipContent: ({ children }) => children,
  TooltipProvider: ({ children }) => children,
  TooltipTrigger: ({ children }) => children,
}));

afterEach(cleanup);

describe("FileItem", () => {
  it("invokes the form preview action", () => {
    const previewAction = vi.fn();

    render(
      <FileItem
        name="customer.form"
        status="success"
        isChecked
        previewAction={previewAction}
        previewTitle="Preview this form"
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "Preview this form" }));

    expect(previewAction).toHaveBeenCalledOnce();
  });
});
