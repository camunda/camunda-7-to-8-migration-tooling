/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.jsx";

const bpmnMocks = vi.hoisted(() => {
  const instances = [];

  class MockBpmnJS {
    constructor(options) {
      this.options = options;
      this.importedXml = [];
      this.destroyed = false;
      this.canvas = {
        zoom: vi.fn(),
        addMarker: vi.fn(),
        removeMarker: vi.fn(),
        scrollToElement: vi.fn(),
      };
      this.selection = { select: vi.fn() };
      // Any element id resolves to a stub element unless explicitly seeded
      // as missing, so tests can assert both "found" and "not found" paths.
      this.missingElementIds = new Set();
      this.elementRegistry = {
        get: vi.fn((id) =>
          this.missingElementIds.has(id) ? undefined : { id, businessObject: {} }
        ),
      };
      instances.push(this);
    }

    importXML(xml) {
      this.importedXml.push(xml);
      return Promise.resolve();
    }

    get(serviceName) {
      if (serviceName === "canvas") return this.canvas;
      if (serviceName === "selection") return this.selection;
      if (serviceName === "elementRegistry") return this.elementRegistry;
      return undefined;
    }

    destroy() {
      this.destroyed = true;
    }
  }

  return { MockBpmnJS, instances };
});

const testState = vi.hoisted(() => ({
  files: [],
  dmnPreviewProps: [],
  formPreviewProps: [],
}));

vi.mock("@camunda/design-system", () => ({
  Alert: ({ title, description, children, className }) => (
    <div role="alert" className={className}>
      <strong>{title}</strong>
      <span>{description}</span>
      {children}
    </div>
  ),
  Button: ({ children, href, ...props }) =>
    href ? (
      <a href={href} {...props}>
        {children}
      </a>
    ) : (
      <button {...props}>{children}</button>
    ),
  Checkbox: ({ id, checked, onCheckedChange, ...props }) => (
    <input
      id={id}
      type="checkbox"
      checked={checked}
      onChange={(event) => onCheckedChange(event.target.checked)}
      {...props}
    />
  ),
  Input: (props) => <input {...props} />,
  Table: ({ children, ...props }) => <table {...props}>{children}</table>,
  TableBody: ({ children, ...props }) => <tbody {...props}>{children}</tbody>,
  TableCell: ({ children, ...props }) => <td {...props}>{children}</td>,
  TableHead: ({ children, ...props }) => <th {...props}>{children}</th>,
  TableHeader: ({ children, ...props }) => <thead {...props}>{children}</thead>,
  TableRow: ({ children, ...props }) => <tr {...props}>{children}</tr>,
  Stepper: ({ children, ...props }) => <div {...props}>{children}</div>,
  StepperStep: ({ children }) => <div>{children}</div>,
  Tooltip: ({ children }) => children,
  TooltipContent: ({ children }) => children,
  TooltipProvider: ({ children }) => children,
  TooltipTrigger: ({ children }) => children,
}));

vi.mock("bpmn-js", () => ({
  default: bpmnMocks.MockBpmnJS,
}));

vi.mock("./DmnPreview", () => ({
  default: (props) => {
    testState.dmnPreviewProps.push(props);
    return <div data-testid="dmn-preview" />;
  },
}));

vi.mock("./DropZone", () => ({
  default: ({ onFiles }) => (
    <button type="button" onClick={() => onFiles(testState.files)}>
      Upload test file
    </button>
  ),
}));

vi.mock("./FormPreview", () => ({
  default: (props) => {
    testState.formPreviewProps.push(props);
    return <div data-testid="form-preview" />;
  },
}));

const fetchMock = vi.fn();

function configureUpload({
  fileName,
  content,
  checkResponseJson,
  convertedContent = content,
}) {
  testState.files.splice(0, testState.files.length, {
    name: fileName,
    text: vi.fn().mockResolvedValue(content),
  });

  fetchMock.mockImplementation((url) => {
    if (url.endsWith("/check")) {
      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        json: vi.fn().mockResolvedValue(checkResponseJson),
      });
    }

    return Promise.resolve({
      ok: true,
      headers: { get: vi.fn().mockReturnValue(null) },
      blob: vi.fn().mockResolvedValue(new Blob([convertedContent])),
    });
  });
}

async function openPreview({
  fileName,
  content,
  checkResponseJson,
  convertedContent,
}) {
  configureUpload({ fileName, content, checkResponseJson, convertedContent });
  render(<App />);

  fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

  const analyzeButton = screen.getByRole("button", {
    name: /Analyze and convert to Camunda/,
  });
  await waitFor(() => expect(analyzeButton.disabled).toBe(false));
  fireEvent.click(analyzeButton);

  const previewButton = await screen.findByRole("button", {
    name: fileName.endsWith(".form")
      ? "Preview form"
      : "Preview analysis findings",
  });
  // Focus before clicking, mirroring how a real click/keyboard activation
  // focuses the button in a browser (jsdom's fireEvent.click doesn't do
  // this on its own) — needed so the preview dialog captures the real
  // opener for focus restoration on close.
  previewButton.focus();
  fireEvent.click(previewButton);

  await screen.findByRole("heading", { name: `Preview: ${fileName}` });
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  testState.files.length = 0;
  testState.dmnPreviewProps.length = 0;
  testState.formPreviewProps.length = 0;
  bpmnMocks.instances.length = 0;
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("analysis findings preview", () => {
  it("renders findings from the analysis response, including documentation links", async () => {
    const documentationUrl = "https://docs.example.com/service-task";
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [
        {
          results: [
            {
              elementType: "bpmn:ServiceTask",
              elementId: "task_1",
              elementName: "Ship order",
              messages: [
                {
                  severity: "WARNING",
                  message: "Review the service task implementation.",
                  link: documentationUrl,
                },
              ],
            },
          ],
        },
      ],
    });

    const table = screen.getByRole("table");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((header) => header.textContent)
    ).toEqual([
      "Element Type",
      "Element ID",
      "Element Name",
      "Severity",
      "Message",
      "Link",
    ]);

    const rows = within(table).getAllByRole("row");
    expect(rows).toHaveLength(2);
    expect(
      within(rows[1])
        .getAllByRole("cell")
        .map((cell) => cell.textContent)
    ).toEqual([
      "bpmn:ServiceTask",
      "task_1",
      "Ship order",
      "WARNING",
      "Review the service task implementation.",
      "Open",
    ]);

    const documentationLink = within(rows[1]).getByRole("link", {
      name: `Open finding documentation: ${documentationUrl}`,
    });
    expect(documentationLink.getAttribute("href")).toBe(documentationUrl);
    expect(documentationLink.getAttribute("target")).toBe("_blank");
  });

  it("renders fallbacks for findings with missing element fields", async () => {
    await openPreview({
      fileName: "customer.form",
      content: JSON.stringify({ type: "default", components: [] }),
      checkResponseJson: [
        {
          results: [
            {
              elementType: null,
              elementId: null,
              elementName: null,
              messages: [
                {
                  severity: "REVIEW",
                  message: "Review this form.",
                },
              ],
            },
          ],
        },
      ],
    });

    const table = screen.getByRole("table");
    const rows = within(table).getAllByRole("row");
    expect(
      within(rows[1])
        .getAllByRole("cell")
        .map((cell) => cell.textContent)
    ).toEqual(["-", "-", "(unnamed)", "REVIEW", "Review this form.", "-"]);
  });

  it("shows an empty state when the analysis response has no findings", async () => {
    await openPreview({
      fileName: "empty.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });

    expect(screen.getByText("No findings for this file.")).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
  });
});

describe("preview routing", () => {
  it.each([
    {
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      expectedType: "bpmn",
    },
    {
      fileName: "decision.dmn",
      content: '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" />',
      expectedType: "dmn",
    },
    {
      fileName: "customer.form",
      content: JSON.stringify({ type: "default", components: [] }),
      expectedType: "form",
    },
  ])("renders the $expectedType preview for a $fileName file", async ({
    fileName,
    content,
    expectedType,
  }) => {
    await openPreview({
      fileName,
      content,
      checkResponseJson: [],
    });

    if (expectedType === "bpmn") {
      await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
      expect(document.querySelector("#bpmnDiagram")).toBeTruthy();
      expect(screen.queryByTestId("dmn-preview")).toBeNull();
      expect(screen.queryByTestId("form-preview")).toBeNull();
      expect(bpmnMocks.instances[0].importedXml).toEqual([content]);
    } else if (expectedType === "dmn") {
      expect(await screen.findByTestId("dmn-preview")).toBeTruthy();
      expect(document.querySelector("#bpmnDiagram")).toBeNull();
      expect(screen.queryByTestId("form-preview")).toBeNull();
      expect(testState.dmnPreviewProps.at(-1).xml).toBe(content);
    } else {
      expect(await screen.findByTestId("form-preview")).toBeTruthy();
      expect(document.querySelector("#bpmnDiagram")).toBeNull();
      expect(screen.queryByTestId("dmn-preview")).toBeNull();
      expect(testState.formPreviewProps.at(-1).schema).toEqual({
        type: "default",
        components: [],
      });
    }
  });

  it("renders the converted form content in the form preview", async () => {
    const originalContent = JSON.stringify({
      type: "default",
      components: [
        {
          type: "textfield",
          key: "customerName",
          defaultValue: "${defaultCustomerName}",
        },
      ],
    });
    const convertedContent = JSON.stringify({
      type: "default",
      components: [
        {
          type: "textfield",
          key: "customerName",
          defaultValue: "= defaultCustomerName",
        },
      ],
    });

    await openPreview({
      fileName: "customer.form",
      content: originalContent,
      convertedContent,
      checkResponseJson: [],
    });

    expect(testState.formPreviewProps.at(-1).schema).toEqual(
      JSON.parse(convertedContent)
    );
  });

  it("renders the converted BPMN content in the preview", async () => {
    const originalContent =
      '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL/"><process id="original" /></definitions>';
    const convertedContent =
      '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL/"><process id="converted" /></definitions>';

    await openPreview({
      fileName: "process.bpmn",
      content: originalContent,
      convertedContent,
      checkResponseJson: [],
    });

    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    expect(bpmnMocks.instances[0].importedXml).toEqual([convertedContent]);
  });

  it("renders the converted DMN content in the preview", async () => {
    const originalContent =
      '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"><decision id="original" /></definitions>';
    const convertedContent =
      '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"><decision id="converted" /></definitions>';

    await openPreview({
      fileName: "decision.dmn",
      content: originalContent,
      convertedContent,
      checkResponseJson: [],
    });

    expect(testState.dmnPreviewProps.at(-1).xml).toBe(convertedContent);
  });
});

describe("finding severity communicates without relying on color alone", () => {
  it.each(["WARNING", "TASK", "REVIEW", "INFO"])(
    "marks a %s finding's diagram element with a distinct highlight class, not always the same one",
    async (severity) => {
      await openPreview({
        fileName: "process.bpmn",
        content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
        checkResponseJson: [
          {
            results: [
              {
                elementId: "task_1",
                elementType: "bpmn:ServiceTask",
                messages: [{ severity, message: "m" }],
              },
            ],
          },
        ],
      });

      await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
      expect(bpmnMocks.instances[0].canvas.addMarker).toHaveBeenCalledWith(
        "task_1",
        `highlight-${severity.toLowerCase()}`
      );
    }
  );

  it("uses the most severe message when an element has several findings", async () => {
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [
        {
          results: [
            {
              elementId: "task_1",
              messages: [
                { severity: "INFO", message: "info" },
                { severity: "WARNING", message: "warning" },
              ],
            },
          ],
        },
      ],
    });

    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    expect(bpmnMocks.instances[0].canvas.addMarker).toHaveBeenCalledWith(
      "task_1",
      "highlight-warning"
    );
  });

  it("styles the file list findings badge by the highest severity, not always warning", async () => {
    configureUpload({
      fileName: "informational.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [
        {
          results: [
            {
              elementId: "el1",
              messages: [{ severity: "INFO", message: "info finding" }],
            },
          ],
        },
      ],
    });

    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));
    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert to Camunda/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);

    const badge = await screen.findByText("1 finding");
    expect(badge.closest("span").className).toContain("fileItemFindingCount-info");
    expect(badge.closest("span").className).not.toContain("fileItemFindingCount-warning");
  });
});

describe("linking a finding row to its diagram element", () => {
  async function openBpmnPreviewWithFindings() {
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [
        {
          results: [
            {
              elementId: "task_1",
              elementType: "bpmn:ServiceTask",
              elementName: "Ship order",
              messages: [{ severity: "WARNING", message: "Review this task." }],
            },
            {
              elementId: null,
              elementType: "bpmn:Process",
              elementName: null,
              messages: [{ severity: "INFO", message: "No stable element reference." }],
            },
          ],
        },
      ],
    });
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    return bpmnMocks.instances[0];
  }

  it("focuses and reveals the matching element when a row with a stable reference is selected", async () => {
    const viewer = await openBpmnPreviewWithFindings();

    const elementLink = screen.getByRole("button", { name: "task_1" });
    fireEvent.click(elementLink);

    expect(viewer.canvas.scrollToElement).toHaveBeenCalledWith(
      expect.objectContaining({ id: "task_1" })
    );
    expect(viewer.selection.select).toHaveBeenCalledWith(
      expect.objectContaining({ id: "task_1" })
    );
    expect(viewer.canvas.addMarker).toHaveBeenCalledWith("task_1", "finding-selected");
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("true");
    expect(elementLink.getAttribute("aria-pressed")).toBeNull();
  });

  it("keeps rows without a stable element reference as plain, non-interactive text", async () => {
    await openBpmnPreviewWithFindings();

    const table = screen.getByRole("table");
    const rows = within(table).getAllByRole("row");
    // Row 2 is the finding without an elementId (rendered as "-").
    const fallbackCell = within(rows[2]).getAllByRole("cell")[1];
    expect(fallbackCell.textContent).toBe("-");
    expect(within(fallbackCell).queryByRole("button")).toBeNull();
  });

  it("does not throw and leaves the row unselected when the element can no longer be found", async () => {
    const viewer = await openBpmnPreviewWithFindings();
    viewer.missingElementIds.add("task_1");

    const elementLink = screen.getByRole("button", { name: "task_1" });
    expect(() => fireEvent.click(elementLink)).not.toThrow();

    expect(viewer.canvas.scrollToElement).not.toHaveBeenCalled();
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBeNull();
  });

  it("does not offer element linking for DMN previews, preserving the graceful fallback", async () => {
    await openPreview({
      fileName: "decision.dmn",
      content: '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" />',
      checkResponseJson: [
        {
          results: [
            {
              elementId: "decision_1",
              elementType: "dmn:decision",
              messages: [{ severity: "WARNING", message: "Review this decision." }],
            },
          ],
        },
      ],
    });

    await screen.findByTestId("dmn-preview");
    const table = screen.getByRole("table");
    expect(within(table).queryByRole("button")).toBeNull();
    expect(within(table).getByText("decision_1")).toBeTruthy();
  });
});

describe("preview overlay behaves as a modal dialog", () => {
  it("moves focus into the dialog and exposes dialog semantics on open", async () => {
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });

    const dialog = screen.getByRole("dialog", { name: "Preview: process.bpmn" });
    expect(dialog.getAttribute("aria-modal")).toBe("true");
    expect(dialog.contains(document.activeElement)).toBe(true);
  });

  it("makes the rest of the page inert and locks background scrolling while open", async () => {
    const previousOverflow = document.body.style.overflow;
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });

    expect(document.querySelector(".pageContent")?.hasAttribute("inert")).toBe(true);
    expect(document.body.style.overflow).toBe("hidden");

    fireEvent.keyDown(document, { key: "Escape" });

    expect(document.querySelector(".pageContent")?.hasAttribute("inert")).toBe(false);
    expect(document.body.style.overflow).toBe(previousOverflow);
  });

  it("closes on Escape and restores focus to the opener", async () => {
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });

    const opener = screen.getByRole("button", { name: "Preview analysis findings" });

    fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByRole("dialog")).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it("traps Tab focus cycling within the dialog's focusable elements", async () => {
    const documentationUrl = "https://docs.example.com/service-task";
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [
        {
          results: [
            {
              elementId: "task_1",
              messages: [{ severity: "WARNING", message: "m", link: documentationUrl }],
            },
          ],
        },
      ],
    });

    const closeButton = screen.getByRole("button", { name: "Close" });
    expect(document.activeElement).toBe(closeButton);

    // Shift+Tab from the first focusable element wraps to the last one.
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true });
    const focusableElements = screen
      .getByRole("dialog")
      .querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
    const last = focusableElements[focusableElements.length - 1];
    expect(document.activeElement).toBe(last);

    // Tab from the last focusable element wraps back to the first.
    fireEvent.keyDown(document, { key: "Tab" });
    expect(document.activeElement).toBe(closeButton);
  });

  it("closes via the Close button and includes the filename in the title", async () => {
    await openPreview({
      fileName: "decision.dmn",
      content: '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" />',
      checkResponseJson: [],
    });

    expect(screen.getByRole("heading", { name: "Preview: decision.dmn" })).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
