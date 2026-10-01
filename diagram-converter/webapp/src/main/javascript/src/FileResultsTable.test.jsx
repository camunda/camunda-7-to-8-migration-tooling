/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FileResultsTable from "./FileResultsTable";

vi.mock("@camunda/design-system", () => ({
  Input: (props) => <input {...props} />,
  Table: ({ children, ...props }) => <table {...props}>{children}</table>,
  TableBody: ({ children, ...props }) => <tbody {...props}>{children}</tbody>,
  TableCell: ({ children, ...props }) => <td {...props}>{children}</td>,
  TableHead: ({ children, ...props }) => <th {...props}>{children}</th>,
  TableHeader: ({ children, ...props }) => <thead {...props}>{children}</thead>,
  TableRow: ({ children, ...props }) => <tr {...props}>{children}</tr>,
}));

afterEach(cleanup);

function row(id, name, highestSeverity, findingCount, overrides = {}) {
  return {
    id,
    name,
    highestSeverity,
    findingCount,
    status: "success",
    isChecked: true,
    isConverted: true,
    previewAction: vi.fn(),
    downloadAction: vi.fn(),
    ...overrides,
  };
}

function fileNamesInOrder() {
  return within(screen.getByRole("table"))
    .getAllByRole("row")
    .slice(1)
    .map((tableRow) => within(tableRow).getAllByRole("cell")[0].textContent);
}

describe("FileResultsTable", () => {
  it("renders result data and sorts by urgency first, with files without findings last", () => {
    render(
      <FileResultsTable
        rows={[
          row("info", "info.bpmn", "INFO", 2),
          row("clean", "clean.dmn", null, 0),
          row("review", "review.bpmn", "REVIEW", 1),
          row("warning", "warning.bpmn", "WARNING", 3),
          row("task", "task.bpmn", "TASK", 1),
        ]}
      />
    );

    const table = screen.getByRole("table", { name: "Batch file results" });
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((header) => header.textContent)
    ).toEqual(["File name", "Finding count", "Severity", "Status", "Actions"]);
    expect(fileNamesInOrder()).toEqual([
      "warning.bpmn",
      "task.bpmn",
      "review.bpmn",
      "info.bpmn",
      "clean.dmn",
    ]);
    expect(
      within(table).getByText("No direct mapping").closest(".severity-cell").className
    ).toContain("severity-cell-warning");
    expect(within(table).getByText("No findings")).toBeTruthy();
    expect(within(table).getByText("3 findings")).toBeTruthy();
  });

  it("shows accessible processing states in a separate status column", () => {
    render(
      <FileResultsTable
        rows={[
          row("analyzing", "analyzing.bpmn", null, 0, {
            status: "uploading",
            isChecked: false,
            isConverted: false,
          }),
          row("converting", "converting.bpmn", null, 0, {
            status: "uploading",
            isChecked: true,
            isConverted: false,
          }),
          row("success", "success.bpmn", null, 0),
          row("failure", "failure.bpmn", null, 0, {
            status: "error",
            isChecked: false,
            isConverted: false,
            error: "Conversion failed.",
          }),
        ]}
      />
    );

    const table = screen.getByRole("table", { name: "Batch file results" });
    const headers = within(table)
      .getAllByRole("columnheader")
      .map((header) => header.textContent);
    const statusColumn = headers.indexOf("Status");
    expect(statusColumn).toBe(3);

    for (const [fileName, statusLabel] of [
      ["analyzing.bpmn", "Analyzing…"],
      ["converting.bpmn", "Converting…"],
      ["success.bpmn", "Success"],
      ["failure.bpmn", "Failed"],
    ]) {
      const tableRow = screen.getByText(fileName).closest("tr");
      const cells = within(tableRow).getAllByRole("cell");
      const statusCell = cells[statusColumn];
      expect(statusCell.textContent).toBe(statusLabel);
      expect(cells[statusColumn + 1].textContent).not.toContain(statusLabel);

      if (statusLabel === "Analyzing…" || statusLabel === "Converting…") {
        expect(within(statusCell).getByRole("status").textContent).toBe(
          statusLabel
        );
      }
    }
  });

  it("sorts numeric finding counts and keeps the selected direction visible", () => {
    render(
      <FileResultsTable
        rows={[
          row("high", "high.bpmn", "WARNING", 5),
          row("low", "low.bpmn", "TASK", 1),
          row("mid", "mid.bpmn", "REVIEW", 3),
          row("none", "none.bpmn", null, 0),
        ]}
      />
    );

    const sortButton = screen.getByRole("button", { name: "Sort by Finding count" });
    fireEvent.click(sortButton);

    expect(fileNamesInOrder()).toEqual([
      "none.bpmn",
      "low.bpmn",
      "mid.bpmn",
      "high.bpmn",
    ]);
    expect(sortButton.closest("th").getAttribute("aria-sort")).toBe("ascending");

    fireEvent.click(sortButton);

    expect(fileNamesInOrder()).toEqual([
      "high.bpmn",
      "mid.bpmn",
      "low.bpmn",
      "none.bpmn",
    ]);
    expect(sortButton.closest("th").getAttribute("aria-sort")).toBe("descending");
  });

  it("combines filename search and severity filters, then clears both", () => {
    render(
      <FileResultsTable
        rows={[
          row("review", "order-review.bpmn", "REVIEW", 2),
          row("info", "order-info.dmn", "INFO", 1),
          row("warning", "invoice-warning.bpmn", "WARNING", 3),
          row("clean", "clean.form", null, 0),
        ]}
      />
    );

    const search = screen.getByRole("searchbox", { name: "Search files" });
    fireEvent.change(search, { target: { value: "ORDER" } });
    fireEvent.click(
      screen.getByRole("button", { name: /No action needed INFO \(1\)/ })
    );

    expect(fileNamesInOrder()).toEqual(["order-review.bpmn"]);
    expect(screen.getByText(/Showing 1 of 4 files/)).toBeTruthy();
    expect(screen.getByText(/Search "ORDER"/)).toBeTruthy();
    expect(
      screen.getByText(/Excluded severities: No action needed \(INFO\)/)
    ).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Clear filters" }));

    expect(search.value).toBe("");
    expect(screen.getByText(/Showing 4 of 4 files/)).toBeTruthy();
    expect(screen.queryByText(/Active filters:/)).toBeNull();
    expect(fileNamesInOrder()).toHaveLength(4);
  });

  it("searches filenames independent of the user's locale", () => {
    const originalToLocaleLowerCase = String.prototype.toLocaleLowerCase;
    const localeSpy = vi
      .spyOn(String.prototype, "toLocaleLowerCase")
      .mockImplementation(function (locales) {
        return originalToLocaleLowerCase.call(this, locales ?? "tr");
      });

    try {
      render(
        <FileResultsTable
          rows={[
            row("invoice", "INVOICE.bpmn", "INFO", 1),
            row("other", "other.bpmn", "INFO", 1),
          ]}
        />
      );

      fireEvent.change(screen.getByRole("searchbox", { name: "Search files" }), {
        target: { value: "invoice" },
      });

      expect(fileNamesInOrder()).toEqual(["INVOICE.bpmn"]);
    } finally {
      localeSpy.mockRestore();
    }
  });

  it("shows an empty-filter state with a visible count and reset action", () => {
    render(
      <FileResultsTable
        rows={[
          row("warning", "warning.bpmn", "WARNING", 1),
          row("info", "info.bpmn", "INFO", 1),
        ]}
      />
    );

    fireEvent.change(screen.getByRole("searchbox", { name: "Search files" }), {
      target: { value: "not-found" },
    });

    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByText("No files match the current filters.")).toBeTruthy();
    expect(screen.getByText(/Showing 0 of 2 files/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Clear filters" })).toBeTruthy();
  });

  it("keeps an active severity filter visible when retry results change", () => {
    const resetKey = {};
    const view = render(
      <FileResultsTable
        resetKey={resetKey}
        rows={[
          row("info", "info.bpmn", "INFO", 1),
          row("warning", "warning.bpmn", "WARNING", 1),
        ]}
      />
    );

    fireEvent.click(
      screen.getByRole("button", { name: /No action needed INFO \(1\)/ })
    );
    view.rerender(
      <FileResultsTable
        resetKey={resetKey}
        rows={[row("warning", "warning.bpmn", "WARNING", 1)]}
      />
    );

    const infoFilter = screen.getByRole("button", {
      name: /No action needed INFO \(0\)/,
    });
    expect(infoFilter.getAttribute("aria-pressed")).toBe("false");
    expect(
      screen.getByText(/Excluded severities: No action needed \(INFO\)/)
    ).toBeTruthy();
    expect(screen.getByText(/Showing 1 of 1 file/)).toBeTruthy();
  });

  it("keeps preview and download actions available in the row", () => {
    const previewAction = vi.fn();
    const downloadAction = vi.fn();
    render(
      <FileResultsTable
        rows={[
          row("file", "process.bpmn", "TASK", 1, {
            previewAction,
            downloadAction,
          }),
        ]}
      />
    );

    fireEvent.click(
      screen.getByRole("button", { name: "Preview analysis findings" })
    );
    fireEvent.click(screen.getByRole("button", { name: "Download process.bpmn" }));

    expect(previewAction).toHaveBeenCalledOnce();
    expect(downloadAction).toHaveBeenCalledOnce();
  });

  it("shows a pending count and accessible retry action for an unanalyzed file", () => {
    render(
      <FileResultsTable
        rows={[
          row("failed", "failed.bpmn", null, 0, {
            isChecked: false,
            isConverted: false,
            status: "error",
            error: "Analysis failed.",
            onRetry: vi.fn(),
          }),
        ]}
      />
    );

    const tableRow = screen.getByText("failed.bpmn").closest("tr");
    expect(within(tableRow).getByText("—")).toBeTruthy();
    expect(within(tableRow).getByText("Not analyzed")).toBeTruthy();
    expect(within(tableRow).getByRole("alert").textContent).toContain(
      "Analysis failed."
    );
    expect(within(tableRow).getByRole("button", { name: "Retry" })).toBeTruthy();
  });
});
