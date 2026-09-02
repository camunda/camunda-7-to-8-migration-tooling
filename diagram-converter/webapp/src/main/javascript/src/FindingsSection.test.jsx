/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FindingsSection from "./FindingsSection";
import { FINDINGS_TABLE_HEADER } from "./findings";

vi.mock("@carbon/react", () => ({
  Table: ({ children, ...props }) => <table {...props}>{children}</table>,
  TableBody: ({ children, ...props }) => <tbody {...props}>{children}</tbody>,
  TableCell: ({ children, ...props }) => <td {...props}>{children}</td>,
  TableHead: ({ children, ...props }) => <thead {...props}>{children}</thead>,
  TableHeader: ({ children, ...props }) => <th {...props}>{children}</th>,
  TableRow: ({ children, ...props }) => <tr {...props}>{children}</tr>,
}));

afterEach(cleanup);

function row(id, severity, overrides = {}) {
  return {
    id,
    elementType: "bpmn:ServiceTask",
    elementId: `element_${id}`,
    elementName: `Element ${id}`,
    severity,
    message: `Message ${id}`,
    link: null,
    ...overrides,
  };
}

function severityCellsInOrder() {
  return within(screen.getByRole("table"))
    .getAllByRole("row")
    .slice(1) // skip header row
    .map((tableRow) => within(tableRow).getAllByRole("cell")[3].textContent);
}

describe("FindingsSection empty state", () => {
  it("shows an empty-findings message and no table when there are no rows", () => {
    render(<FindingsSection header={FINDINGS_TABLE_HEADER} rows={[]} />);

    expect(screen.getByText("No findings for this file.")).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByRole("group", { name: "Filter findings by severity" })).toBeNull();
  });
});

describe("FindingsSection informational-only state", () => {
  it("renders a plain-language label without a severity filter when only one severity is present", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "INFO"), row(2, "INFO")]}
      />
    );

    // A single-severity result set needs no filter chips (nothing to filter between).
    expect(screen.queryByRole("group", { name: "Filter findings by severity" })).toBeNull();
    expect(severityCellsInOrder()).toEqual([
      "No action needed (INFO)",
      "No action needed (INFO)",
    ]);
  });
});

describe("FindingsSection labels and legend", () => {
  it("translates every raw severity code to a plain-language label in the table", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "WARNING"), row(2, "TASK"), row(3, "REVIEW"), row(4, "INFO")]}
      />
    );

    expect(severityCellsInOrder()).toEqual([
      "No direct mapping (WARNING)",
      "Manual action required (TASK)",
      "Verify after conversion (REVIEW)",
      "No action needed (INFO)",
    ]);
  });

  it("keeps unknown severity codes in the table, filter and legend", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "FUTURE"), row(2, "INFO")]}
      />
    );

    expect(severityCellsInOrder()).toEqual([
      "No action needed (INFO)",
      "FUTURE (FUTURE)",
    ]);
    expect(screen.getByRole("button", { name: /FUTURE FUTURE \(1\)/ })).toBeTruthy();

    fireEvent.click(screen.getByText("What do these severities mean?"));

    expect(screen.getAllByRole("term").map((term) => term.textContent)).toContain("FUTURE FUTURE");
  });

  it("normalizes missing severity values for display and filtering", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, null), row(2, undefined), row(3, "INFO")]}
      />
    );

    expect(severityCellsInOrder()).toEqual([
      "No action needed (INFO)",
      "Unknown (Unknown)",
      "Unknown (Unknown)",
    ]);

    const unknownChip = screen.getByRole("button", { name: /Unknown Unknown \(2\)/ });
    fireEvent.click(unknownChip);

    expect(severityCellsInOrder()).toEqual(["No action needed (INFO)"]);
  });

  it("only lists severities present in the current result set in the legend", () => {
    render(
      <FindingsSection header={FINDINGS_TABLE_HEADER} rows={[row(1, "TASK"), row(2, "INFO")]} />
    );

    fireEvent.click(screen.getByText("What do these severities mean?"));

    expect(screen.getByText("Manual changes are required to make this element work in Camunda 8.")).toBeTruthy();
    expect(screen.getByText("This was converted automatically and needs no follow-up.")).toBeTruthy();
    expect(screen.queryByText("A Camunda 7 concept can't be directly mapped to a Camunda 8 equivalent. Review the Camunda 8 roadmap or explore a workaround.")).toBeNull();
    expect(screen.queryByText("The conversion changed an expression or attribute. Verify that the intended behavior is unchanged.")).toBeNull();
  });
});

describe("FindingsSection sorting", () => {
  it("sorts rows by severity so the most urgent findings appear first regardless of input order", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "INFO"), row(2, "REVIEW"), row(3, "WARNING"), row(4, "TASK")]}
      />
    );

    expect(severityCellsInOrder()).toEqual([
      "No direct mapping (WARNING)",
      "Manual action required (TASK)",
      "Verify after conversion (REVIEW)",
      "No action needed (INFO)",
    ]);
  });
});

describe("FindingsSection filtering (mixed-severity state)", () => {
  it("shows severity chips with counts and hides a severity's rows when its chip is toggled off", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "WARNING"), row(2, "WARNING"), row(3, "INFO")]}
      />
    );

    const warningChip = screen.getByRole("button", { name: /No direct mapping WARNING \(2\)/ });
    const infoChip = screen.getByRole("button", { name: /No action needed INFO \(1\)/ });
    expect(warningChip.getAttribute("aria-pressed")).toBe("true");
    expect(infoChip.getAttribute("aria-pressed")).toBe("true");

    fireEvent.click(infoChip);

    expect(infoChip.getAttribute("aria-pressed")).toBe("false");
    expect(severityCellsInOrder()).toEqual([
      "No direct mapping (WARNING)",
      "No direct mapping (WARNING)",
    ]);
    expect(screen.getByText(/Showing 2 of 3 findings/)).toBeTruthy();
  });

  it("returns to the complete list without losing the result set via 'Show all findings'", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "WARNING"), row(2, "INFO")]}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /No action needed INFO \(1\)/ }));
    expect(severityCellsInOrder()).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "Show all findings" }));

    expect(screen.queryByText(/Showing \d+ of \d+ findings/)).toBeNull();
    expect(severityCellsInOrder()).toHaveLength(2);
  });

  it("resets filters when a new result set is loaded", () => {
    const firstRows = [row(1, "WARNING"), row(2, "INFO")];
    const nextRows = [row(3, "WARNING"), row(4, "INFO")];
    const view = render(<FindingsSection header={FINDINGS_TABLE_HEADER} rows={firstRows} />);

    fireEvent.click(screen.getByRole("button", { name: /No action needed INFO \(1\)/ }));
    expect(severityCellsInOrder()).toHaveLength(1);

    view.rerender(<FindingsSection header={FINDINGS_TABLE_HEADER} rows={nextRows} />);

    expect(severityCellsInOrder()).toHaveLength(2);
    expect(screen.queryByText(/Showing \d+ of \d+ findings/)).toBeNull();
  });

  it("shows a clear message when every visible severity is filtered out", () => {
    render(
      <FindingsSection
        header={FINDINGS_TABLE_HEADER}
        rows={[row(1, "WARNING"), row(2, "INFO")]}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /No direct mapping WARNING \(1\)/ }));
    fireEvent.click(screen.getByRole("button", { name: /No action needed INFO \(1\)/ }));

    expect(screen.getByText("No findings match the selected severities.")).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
  });
});

describe("FindingsSection large result sets", () => {
  it("renders every finding and keeps filtering actionable for a long list", () => {
    const rows = Array.from({ length: 40 }, (_, i) =>
      row(i, i % 4 === 0 ? "WARNING" : i % 4 === 1 ? "TASK" : i % 4 === 2 ? "REVIEW" : "INFO")
    );

    render(<FindingsSection header={FINDINGS_TABLE_HEADER} rows={rows} />);

    expect(severityCellsInOrder()).toHaveLength(40);

    fireEvent.click(screen.getByRole("button", { name: /No action needed INFO \(10\)/ }));

    expect(severityCellsInOrder()).toHaveLength(30);
    expect(screen.getByText(/Showing 30 of 40 findings/)).toBeTruthy();
  });
});
