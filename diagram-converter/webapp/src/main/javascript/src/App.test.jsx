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
  formPreviewProps: [],
}));

vi.mock("@carbon/react", () => {
  const omitProps = (props, omitted) =>
    Object.fromEntries(
      Object.entries(props).filter(([name]) => !omitted.includes(name)),
    );

  return {
    ProgressIndicator: ({ children, ...props }) => (
      <div {...omitProps(props, ["spaceEqually"])}>{children}</div>
    ),
    ProgressStep: ({ children }) => <div>{children}</div>,
    Button: ({ children, href, ...props }) => {
      const buttonProps = omitProps(props, ["renderIcon", "kind", "size"]);

      return href ? (
        <a href={href} {...buttonProps}>
          {children}
        </a>
      ) : (
        <button type="button" {...buttonProps}>
          {children}
        </button>
      );
    },
    Callout: () => null,
    Table: ({ children, ...props }) => <table {...props}>{children}</table>,
    TableHead: ({ children, ...props }) => <thead {...props}>{children}</thead>,
    TableRow: ({ children, ...props }) => <tr {...props}>{children}</tr>,
    TableHeader: ({ children, ...props }) => <th {...props}>{children}</th>,
    TableBody: ({ children, ...props }) => <tbody {...props}>{children}</tbody>,
    TableCell: ({ children, ...props }) => <td {...props}>{children}</td>,
    Form: ({ children, ...props }) => <form {...props}>{children}</form>,
    FormGroup: ({ children, ...props }) => <fieldset {...props}>{children}</fieldset>,
    Checkbox: ({ id, checked, onChange, ...props }) => (
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event, { checked: event.target.checked })}
        {...omitProps(props, ["labelText", "helperText"])}
      />
    ),
    TextInput: ({ ...props }) => (
      <input {...omitProps(props, ["labelText", "helperText"])} />
    ),
    Loading: () => null,
  };
});

vi.mock("@carbon/react/icons", () => ({
  CheckmarkFilled: () => null,
  Close: () => null,
  Download: () => null,
  Launch: () => null,
  Settings: () => null,
  TrashCan: () => null,
  View: () => null,
  WarningFilled: () => null,
}));

vi.mock("bpmn-js", () => ({
  default: bpmnMocks.MockBpmnJS,
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
    name: /Analyze and convert/,
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

function deferred() {
  let resolve;
  const promise = new Promise((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

// A real File (not a plain mock) so FormData/fetch mocks that need to tell
// files apart by name (e.g. retry-only-the-failed-file) can read it back via
// formData.get("file").name.
function mockFile(name, content = "<xml/>") {
  return new File([content], name);
}

async function uploadAndAnalyze(files) {
  testState.files.splice(0, testState.files.length, ...files);
  render(<App />);

  fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

  const analyzeButton = screen.getByRole("button", {
    name: /Analyze and convert/,
  });
  await waitFor(() => expect(analyzeButton.disabled).toBe(false));
  fireEvent.click(analyzeButton);
}

function fileRow(fileName) {
  return screen.getByText(fileName).closest(".FileItem");
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  testState.files.length = 0;
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
      "Link",
    ]);

    const documentationLink = within(rows[1]).getByRole("link");
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
    } else {
      expect(await screen.findByTestId("form-preview")).toBeTruthy();
      expect(document.querySelector("#bpmnDiagram")).toBeNull();
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
});

describe("upload onboarding guidance", () => {
  // The 95-file batch limit mirrors MAX_BATCH_FILES in App.jsx: the server
  // accepts up to 100 multipart parts (server.tomcat.max-part-count), and
  // createFormData() always appends 5 non-file fields, leaving 95 parts
  // available for files.
  const MAX_BATCH_FILES = 95;
  const BATCH_FILE_WARNING_THRESHOLD = 86;

  it("states the batch limit and hosted-processing disclosure before any files are uploaded", () => {
    render(<App />);

    expect(screen.getByText(/up to 95 files per batch/i)).toBeTruthy();
    expect(
      screen.getByText(/sent to Camunda's hosted service for/i)
    ).toBeTruthy();

    const localConverterLinks = [
      screen.getByRole("link", { name: /run the diagram converter locally/i }),
      screen.getByRole("link", { name: /use the local converter/i }),
    ];
    localConverterLinks.forEach((link) => {
      expect(link.getAttribute("href")).toBe(
        "https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/#local-web-application"
      );
      expect(link.getAttribute("target")).toBe("_blank");
    });

    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("does not warn about the batch limit for a small number of files", () => {
    testState.files.splice(
      0,
      testState.files.length,
      ...Array.from({ length: 5 }, (_, i) => ({
        name: `model-${i}.bpmn`,
        text: vi.fn(),
      }))
    );

    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("warns when approaching the batch limit", () => {
    testState.files.splice(
      0,
      testState.files.length,
      ...Array.from({ length: BATCH_FILE_WARNING_THRESHOLD }, (_, i) => ({
        name: `model-${i}.bpmn`,
        text: vi.fn(),
      }))
    );

    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    const alert = screen.getByRole("alert");
    expect(alert.textContent).toMatch(
      new RegExp(`Approaching the batch limit \\(${BATCH_FILE_WARNING_THRESHOLD} of ${MAX_BATCH_FILES} files\\)`)
    );
  });

  it("reports the batch limit as reached once the limit is met", () => {
    testState.files.splice(
      0,
      testState.files.length,
      ...Array.from({ length: MAX_BATCH_FILES }, (_, i) => ({
        name: `model-${i}.bpmn`,
        text: vi.fn(),
      }))
    );

    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    const alert = screen.getByRole("alert");
    expect(alert.textContent).toMatch(
      new RegExp(`Batch limit reached \\(${MAX_BATCH_FILES} files\\)`)
    );
  });

  it("reports the batch limit as exceeded, with the fixed limit and current count, past the limit", () => {
    const uploadedCount = MAX_BATCH_FILES + 3;
    testState.files.splice(
      0,
      testState.files.length,
      ...Array.from({ length: uploadedCount }, (_, i) => ({
        name: `model-${i}.bpmn`,
        text: vi.fn(),
      }))
    );

    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    const alert = screen.getByRole("alert");
    expect(alert.textContent).toMatch(
      new RegExp(`Batch limit exceeded \\(${MAX_BATCH_FILES} max, ${uploadedCount} added\\)`)
    );
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

  it("keeps findings visible with an info highlight when severities are unknown", async () => {
    await openPreview({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [
        {
          results: [
            {
              elementId: "task_1",
              messages: [
                { severity: "UNKNOWN", message: "Unrecognized severity." },
                { message: "Missing severity." },
              ],
            },
          ],
        },
      ],
    });

    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    expect(bpmnMocks.instances[0].canvas.addMarker).toHaveBeenCalledWith(
      "task_1",
      "highlight-info"
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
      name: /Analyze and convert/,
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
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("false");
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
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("false");
  });

  it("does not offer element linking for unsupported previews", async () => {
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
  expect(screen.queryByRole("dialog")).toBeNull();
});
});

describe("per-file request failures and retry", () => {
  it("stops the spinner and shows an accessible retry-able error when the network request fails", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) return Promise.reject(new TypeError("Failed to fetch"));
      throw new Error("convert should not be called when /check fails");
    });

    await uploadAndAnalyze([
      { name: "offline.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    const row = await screen.findByText("offline.bpmn").then((el) => el.closest(".FileItem"));
    const alert = await within(row).findByRole("alert");
    expect(alert.textContent).toMatch(/could not reach the server/i);

    // The spinner/status must be gone once the row is in an error state.
    expect(within(row).queryByRole("status")).toBeNull();

    // Retry is offered and reprocesses without a full page reload.
    expect(within(row).getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("surfaces a plain-language error and offers retry for a non-2xx analyze response", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: false,
          status: 500,
          headers: { get: vi.fn().mockReturnValue(null) },
          text: vi.fn().mockResolvedValue(""),
        });
      }
      throw new Error("convert should not be called when /check is non-2xx");
    });

    await uploadAndAnalyze([
      { name: "server-error.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    const row = fileRow("server-error.bpmn");
    const alert = await within(row).findByRole("alert");
    expect(alert.textContent).toMatch(/analysis failed \(http 500\)/i);
    expect(within(row).getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("surfaces a plain-language error when the analyze response body cannot be parsed", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockRejectedValue(new SyntaxError("Unexpected token")),
        });
      }
      throw new Error("convert should not be called when /check response is malformed");
    });

    await uploadAndAnalyze([
      { name: "malformed.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    const row = fileRow("malformed.bpmn");
    const alert = await within(row).findByRole("alert");
    expect(alert.textContent).toMatch(/analysis response could not be read/i);
  });

  it("retries only the failed file, preserving other completed rows and their converted content", async () => {
    let badConvertAttempts = 0;

    fetchMock.mockImplementation((url, options) => {
      const fileName = options.body.get("file").name;

      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue([]),
        });
      }

      // /convert
      if (fileName === "bad.bpmn" && badConvertAttempts === 0) {
        badConvertAttempts += 1;
        return Promise.resolve({
          ok: false,
          status: 502,
          headers: { get: vi.fn().mockReturnValue(null) },
          text: vi.fn().mockResolvedValue(""),
        });
      }

      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        blob: vi.fn().mockResolvedValue(new Blob(["converted"])),
      });
    });

    await uploadAndAnalyze([
      mockFile("good.bpmn"),
      mockFile("bad.bpmn"),
    ]);

    const goodRow = await screen.findByText("good.bpmn").then((el) => el.closest(".FileItem"));
    await within(goodRow).findByRole("button", { name: "Download converted model" });

    const badRow = fileRow("bad.bpmn");
    await within(badRow).findByRole("alert");
    const callsBeforeRetry = fetchMock.mock.calls.length;

    fireEvent.click(within(badRow).getByRole("button", { name: "Retry" }));

    await within(badRow).findByRole("button", { name: "Download converted model" });

    // Retry only re-ran /check + /convert for the failed file.
    expect(fetchMock.mock.calls.length).toBe(callsBeforeRetry + 2);

    // The other, already-completed row was left untouched.
    expect(within(goodRow).getByRole("button", { name: "Download converted model" })).toBeTruthy();
    expect(within(goodRow).queryByRole("alert")).toBeNull();
  });
});

describe("navigation between configure and results", () => {
  it("returns to configure without discarding the uploaded file list", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue([]),
        });
      }
      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        blob: vi.fn().mockResolvedValue(new Blob(["converted"])),
      });
    });

    await uploadAndAnalyze([
      { name: "keep-me.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    await screen.findByRole("heading", { name: "Converted files" });

    fireEvent.click(screen.getByRole("button", { name: "Back to configure" }));

    expect(await screen.findByRole("heading", { name: "Add files" })).toBeTruthy();
    expect(screen.getByText("keep-me.bpmn")).toBeTruthy();
  });

  it("starts a new batch that clears the previous files and results", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue([]),
        });
      }
      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        blob: vi.fn().mockResolvedValue(new Blob(["converted"])),
      });
    });

    await uploadAndAnalyze([
      { name: "replace-me.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    await screen.findByRole("heading", { name: "Converted files" });

    fireEvent.click(screen.getByRole("button", { name: "Convert more files" }));

    expect(await screen.findByRole("heading", { name: "Add files" })).toBeTruthy();
    expect(screen.queryByText("replace-me.bpmn")).toBeNull();
  });
});

describe("migration guide action", () => {
  it("renders a real, keyboard-operable link that reaches the migration guide", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue([]),
        });
      }
      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        blob: vi.fn().mockResolvedValue(new Blob(["converted"])),
      });
    });

    await uploadAndAnalyze([
      { name: "any.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    const link = await screen.findByRole("link", { name: /open migration guide/i });
    expect(link.tagName).toBe("A");
    expect(link.getAttribute("href")).toBe(
      "https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-journey/?utm_source=analyzer"
    );
    expect(link.getAttribute("target")).toBe("_blank");
    expect(link.getAttribute("rel")).toBe("noopener noreferrer");
  });
});

describe("single loading indicator per file", () => {
  it("shows exactly one spinner and status label per phase instead of two", async () => {
    const checkDeferred = deferred();
    const convertDeferred = deferred();

    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) return checkDeferred.promise;
      return convertDeferred.promise;
    });

    await uploadAndAnalyze([
      { name: "slow.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    const row = fileRow("slow.bpmn");

    // Analyzing phase: exactly one status indicator, not two.
    await waitFor(() => expect(within(row).getAllByRole("status")).toHaveLength(1));
    expect(within(row).getByRole("status").textContent).toMatch(/analyzing/i);

    checkDeferred.resolve({
      ok: true,
      headers: { get: vi.fn().mockReturnValue(null) },
      json: vi.fn().mockResolvedValue([]),
    });

    // Converting phase: still exactly one status indicator.
    await waitFor(() =>
      expect(within(row).getByRole("status").textContent).toMatch(/converting/i)
    );
    expect(within(row).getAllByRole("status")).toHaveLength(1);

    convertDeferred.resolve({
      ok: true,
      headers: { get: vi.fn().mockReturnValue(null) },
      blob: vi.fn().mockResolvedValue(new Blob(["converted"])),
    });

    await within(row).findByRole("button", { name: "Download converted model" });
    expect(within(row).queryByRole("status")).toBeNull();
  });
});
