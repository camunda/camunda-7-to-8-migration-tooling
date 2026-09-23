/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import DropZone from "./DropZone";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("DropZone", () => {
  it("does not mark up its instruction as a heading, so it never competes with the page title", () => {
    render(<DropZone onFiles={vi.fn()} />);

    expect(
      screen.queryByRole("heading", {
        name: "Click or drag files here to upload",
      })
    ).toBeNull();

    const instruction = screen.getByText("Click or drag files here to upload");
    expect(instruction.tagName).toBe("P");
  });

  it("activates the underlying file picker on both Enter and Space", () => {
    const clickSpy = vi
      .spyOn(HTMLInputElement.prototype, "click")
      .mockImplementation(() => {});

    render(<DropZone onFiles={vi.fn()} />);

    const dropZone = screen.getByRole("button", {
      name: /Click or drag files here to upload/,
    });
    expect(dropZone.getAttribute("tabindex")).toBe("0");

    fireEvent.keyDown(dropZone, { key: "Enter" });
    expect(clickSpy).toHaveBeenCalledTimes(1);

    // Space only activates on keyup (native button semantics), so keydown
    // alone must not trigger the file picker a second time.
    fireEvent.keyDown(dropZone, { key: " " });
    expect(clickSpy).toHaveBeenCalledTimes(1);
    fireEvent.keyUp(dropZone, { key: " " });
    expect(clickSpy).toHaveBeenCalledTimes(2);
  });
});
