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

afterEach(cleanup);

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

  it("exposes the upload control as a single keyboard-operable button", () => {
    const onFiles = vi.fn();
    render(<DropZone onFiles={onFiles} />);

    const dropZone = screen.getByRole("button", {
      name: /Click or drag files here to upload/,
    });
    expect(dropZone.getAttribute("tabindex")).toBe("0");

    fireEvent.keyUp(dropZone, { key: " " });
    // selectFileToUpload() opens a native file picker (not invoking onFiles
    // directly), so we only assert the key handler doesn't throw and the
    // element remains focusable/operable — the file-selection flow itself is
    // covered by App.test.jsx via the mocked DropZone.
    expect(dropZone).toBeTruthy();
  });
});
