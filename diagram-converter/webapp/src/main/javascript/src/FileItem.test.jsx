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
        previewTitle="Preview form"
      />
    );

    const previewButton = screen.getByRole("button", { name: "Preview form" });
    expect(previewButton.type).toBe("button");

    fireEvent.click(previewButton);

    expect(previewAction).toHaveBeenCalledOnce();
  });

  it("gives the remove-file button an accessible name that includes the filename", () => {
    const onDelete = vi.fn();

    render(<FileItem name="invoice.bpmn" status="edit" onDelete={onDelete} />);

    const removeButton = screen.getByRole("button", {
      name: "Remove invoice.bpmn",
    });
    expect(removeButton.tagName).toBe("BUTTON");
    expect(removeButton.type).toBe("button");

    fireEvent.click(removeButton);
    expect(onDelete).toHaveBeenCalledOnce();
  });

  it("gives each remove-file button a distinct accessible name across a batch of files", () => {
    render(
      <>
        <FileItem name="order.bpmn" status="edit" onDelete={vi.fn()} />
        <FileItem name="claim.dmn" status="edit" onDelete={vi.fn()} />
      </>
    );

    expect(
      screen.getByRole("button", { name: "Remove order.bpmn" })
    ).toBeTruthy();
    expect(
      screen.getByRole("button", { name: "Remove claim.dmn" })
    ).toBeTruthy();
  });

  it("gives the download button an accessible name that includes the filename", () => {
    const downloadAction = vi.fn();

    render(
      <FileItem
        name="process.bpmn"
        status="success"
        isConverted
        downloadAction={downloadAction}
      />
    );

    const downloadButton = screen.getByRole("button", {
      name: "Download process.bpmn",
    });
    expect(downloadButton.tagName).toBe("BUTTON");
    expect(downloadButton.type).toBe("button");

    fireEvent.click(downloadButton);
    expect(downloadAction).toHaveBeenCalledOnce();
  });

  it("does not render the filename as a fake clickable link", () => {
    const downloadAction = vi.fn();

    render(
      <FileItem
        name="process.bpmn"
        status="success"
        isConverted
        downloadAction={downloadAction}
      />
    );

    const filename = screen.getByText("process.bpmn");
    expect(filename.tagName).toBe("SPAN");
    expect(filename.getAttribute("role")).toBeNull();
    expect(filename.getAttribute("tabindex")).toBeNull();

    fireEvent.click(filename);
    expect(downloadAction).not.toHaveBeenCalled();
  });
});
