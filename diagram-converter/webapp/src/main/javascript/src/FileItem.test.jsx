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

  it("exposes the full filename via a title attribute so long, truncated names stay reachable", () => {
    const longName =
      "a-very-long-process-definition-name-that-would-otherwise-break-the-layout.bpmn";

    render(<FileItem name={longName} status="edit" />);

    expect(screen.getByText(longName).getAttribute("title")).toBe(longName);
  });

  it("renders no findings badge when there are no findings", () => {
    render(<FileItem name="clean.bpmn" status="success" findingCount={0} />);

    expect(screen.queryByText(/finding/)).toBeNull();
  });

  it("styles the badge for the most severe finding, not warning by default", () => {
    render(
      <FileItem
        name="informational.bpmn"
        status="success"
        findingCount={3}
        highestSeverity="INFO"
      />
    );

    const badge = screen.getByText("3 findings").closest("span");
    expect(badge.className).toContain("fileItemFindingCount-info");
    expect(badge.className).not.toContain("fileItemFindingCount-warning");
    expect(badge.getAttribute("title")).toBe("Highest severity: INFO");
    expect(badge.getAttribute("aria-label")).toBe("3 findings, highest severity INFO");
  });

  it("styles the badge for the highest severity in a mixed-severity file", () => {
    render(
      <FileItem
        name="mixed.bpmn"
        status="success"
        findingCount={5}
        highestSeverity="WARNING"
      />
    );

    const badge = screen.getByText("5 findings").closest("span");
    expect(badge.className).toContain("fileItemFindingCount-warning");
    expect(badge.getAttribute("title")).toBe("Highest severity: WARNING");
  });

  it("does not claim an INFO severity when the highest severity is unknown", () => {
    render(
      <FileItem name="unrecognized.bpmn" status="success" findingCount={2} highestSeverity={null} />
    );

    const badge = screen.getByText("2 findings").closest("span");
    expect(badge.className).toContain("fileItemFindingCount-info");
    expect(badge.getAttribute("title")).toBe("Highest severity: Unknown");
    expect(badge.getAttribute("aria-label")).toBe("2 findings, highest severity Unknown");
  });

  it("uses singular finding text for a single finding", () => {
    render(
      <FileItem
        name="single.bpmn"
        status="success"
        findingCount={1}
        highestSeverity="TASK"
      />
    );

    expect(screen.getByText("1 finding")).toBeTruthy();
  });

  it("exposes the badge severity to assistive technology", () => {
    render(
      <FileItem
        name="review.bpmn"
        status="success"
        findingCount={2}
        highestSeverity="REVIEW"
      />
    );
    expect(screen.getByLabelText("2 findings, highest severity REVIEW")).toBeTruthy();
  });
});
