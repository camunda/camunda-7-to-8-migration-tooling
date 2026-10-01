/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { readFileSync } from "node:fs";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import App from "./App.jsx";

const appCss = readFileSync("src/index.css", "utf8");

const bpmnMocks = vi.hoisted(() => {
  const instances = [];
  const missingElementIds = new Set();

  class MockBpmnJS {
    constructor(options) {
      this.options = options;
      this.importedXml = [];
      this.destroyed = false;
      this.selectionChangedListener = null;
      this.eventBus = {
        on: vi.fn((eventName, listener) => {
          if (eventName === "selection.changed") {
            this.selectionChangedListener = listener;
          }
        }),
        off: vi.fn((eventName, listener) => {
          if (eventName === "selection.changed" && this.selectionChangedListener === listener) {
            this.selectionChangedListener = null;
          }
        }),
        fireSelectionChanged: (newSelection) =>
          this.selectionChangedListener?.({ newSelection }),
      };
      // Any element id resolves to a stub element unless explicitly seeded
      // as missing, so tests can assert both "found" and "not found" paths.
      this.missingElementIds = new Set(missingElementIds);
      this.elementRegistry = {
        get: vi.fn((id) =>
          this.missingElementIds.has(id) ? undefined : { id, businessObject: {} }
        ),
      };
      this.canvas = {
        zoom: vi.fn(),
        addMarker: vi.fn((elementId) => {
          if (!this.elementRegistry.get(elementId)) {
            throw new Error(`Cannot add marker to missing element ${elementId}`);
          }
        }),
        removeMarker: vi.fn(),
        scrollToElement: vi.fn(),
      };
      this.selection = { select: vi.fn() };
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
      if (serviceName === "eventBus") return this.eventBus;
      return undefined;
    }

    destroy() {
      this.destroyed = true;
    }
  }

  return { MockBpmnJS, instances, missingElementIds };
});

const testState = vi.hoisted(() => ({
  files: [],
  dmnPreviewProps: [],
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
    TextInput: ({ labelText, ...props }) => (
      <input aria-label={labelText} {...omitProps(props, ["helperText"])} />
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

vi.mock("./DmnPreview", () => ({
  default: (props) => {
    testState.dmnPreviewProps.push(props);
    return <div data-testid="dmn-preview" />;
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
  missingElementIds = [],
}) {
  missingElementIds.forEach((id) => bpmnMocks.missingElementIds.add(id));
  configureUpload({ fileName, content, checkResponseJson, convertedContent });
  render(<App />);

  fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

  const analyzeButton = screen.getByRole("button", {
    name: /Analyze and convert/,
  });
  await waitFor(() => expect(analyzeButton.disabled).toBe(false));
  fireEvent.click(analyzeButton);
  await screen.findByRole("button", { name: `Download ${fileName}` });

  const previewButton = await screen.findByRole("button", {
    name: fileName.endsWith(".form")
      ? `Preview form for ${fileName}`
      : `Preview analysis findings for ${fileName}`,
  });
  // Focus before clicking, mirroring how a real click/keyboard activation
  // focuses the button in a browser (jsdom's fireEvent.click doesn't do
  // this on its own) — needed so the preview dialog captures the real
  // opener for focus restoration on close.
  previewButton.focus();
  fireEvent.click(previewButton);

  await screen.findByRole("heading", { name: `Preview: ${fileName}` });
  await waitFor(() =>
    expect(screen.queryByText("Loading preview…")).toBeNull()
  );
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

function configureBatchResponses(responsesByFile) {
  fetchMock.mockImplementation((url, options) => {
    const fileName = options.body.get("file").name;
    const response = responsesByFile[fileName];
    if (!response) {
      throw new Error(`Unexpected request for ${fileName}`);
    }

    if (url.endsWith("/check")) {
      if (response.analysisError) {
        return Promise.resolve({
          ok: false,
          status: 500,
          headers: { get: vi.fn().mockReturnValue(null) },
          text: vi.fn().mockResolvedValue(response.analysisError),
        });
      }

      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        json: vi.fn().mockResolvedValue(response.checkResponseJson),
      });
    }

    if (response.conversionError) {
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
      blob: vi
        .fn()
        .mockResolvedValue(new Blob([response.convertedContent || ""])),
    });
  });
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
  return screen.getByText(fileName).closest(".file-result-row");
}

let originalScrollIntoView;
let scrollIntoView;

async function waitForProcessingToFinish() {
  await waitFor(() =>
    expect(
      screen
        .queryAllByRole("status")
        .filter((status) => /^(Analyzing|Converting)…$/.test(status.textContent))
    ).toHaveLength(0)
  );
}

async function openUploadedPreview(fileName) {
  await waitForProcessingToFinish();

  const previewButtonName = fileName.endsWith(".form")
    ? `Preview form for ${fileName}`
    : `Preview analysis findings for ${fileName}`;
  const previewButton = await within(fileRow(fileName)).findByRole("button", {
    name: previewButtonName,
  });
  previewButton.focus();
  fireEvent.click(previewButton);

  await screen.findByRole("heading", { name: `Preview: ${fileName}` });
  await waitFor(() =>
    expect(screen.queryByText("Loading preview…")).toBeNull()
  );
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  testState.files.length = 0;
  testState.dmnPreviewProps.length = 0;
  testState.formPreviewProps.length = 0;
  bpmnMocks.instances.length = 0;
  bpmnMocks.missingElementIds.clear();
  originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
  scrollIntoView = vi.fn();
  HTMLElement.prototype.scrollIntoView = scrollIntoView;
});

afterEach(() => {
  cleanup();
  if (originalScrollIntoView) {
    HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
  } else {
    delete HTMLElement.prototype.scrollIntoView;
  }
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

    const table = screen.getByRole("table", { name: "Findings for this file" });
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((header) => header.textContent)
    ).toEqual([
      "Element type",
      "Element ID",
      "Element name",
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
      "No direct mapping (WARNING)",
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

    const table = screen.getByRole("table", { name: "Findings for this file" });
    const rows = within(table).getAllByRole("row");
    expect(
      within(rows[1])
        .getAllByRole("cell")
        .map((cell) => cell.textContent)
    ).toEqual(["-", "-", "(unnamed)", "Verify after conversion (REVIEW)", "Review this form.", "-"]);
  });

  it("shows an empty state when the analysis response has no findings", async () => {
    await openPreview({
      fileName: "empty.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });

    expect(screen.getByText("No findings for this file.")).toBeTruthy();
    expect(
      screen.queryByRole("table", { name: "Findings for this file" })
    ).toBeNull();
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

describe("preview navigation", () => {
  it("keeps navigation sticky at the top while the preview dialog scrolls", async () => {
    const scrollContainerRule = appCss.match(/\.modal\s*\{[^}]*overflow-y:\s*auto;/);
    const navigationRule = appCss.match(/\.preview-navigation\s*\{[^}]*\}/);
    expect(scrollContainerRule).not.toBeNull();
    expect(navigationRule).not.toBeNull();

    const stylesheet = document.createElement("style");
    stylesheet.textContent = `${scrollContainerRule[0]}}\n${navigationRule[0]}`;
    document.head.append(stylesheet);

    try {
      await openPreview({
        fileName: "process.bpmn",
        content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
        checkResponseJson: [],
      });

      const dialog = screen.getByRole("dialog");
      const navigation = within(dialog).getByRole("navigation", {
        name: "File preview navigation",
      });
      expect(window.getComputedStyle(dialog).overflowY).toBe("auto");
      expect(window.getComputedStyle(navigation).position).toBe("sticky");
      expect(window.getComputedStyle(navigation).top).toBe("0px");
    } finally {
      stylesheet.remove();
    }
  });

  it("shows the position and disables navigation for a single-file batch", async () => {
    await openPreview({
      fileName: "only.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("1 of 1")).toBeTruthy();
    expect(within(dialog).getByRole("button", { name: "Previous file" }).disabled).toBe(
      true
    );
    expect(within(dialog).getByRole("button", { name: "Next file" }).disabled).toBe(
      true
    );
    expect(
      within(dialog).getByRole("button", { name: "Next with findings" }).disabled
    ).toBe(true);
  });

  it("jumps over files without findings and disables controls at the result boundaries", async () => {
    const bpmn = '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />';
    const oneFinding = [
      {
        results: [
          {
            elementId: "task",
            messages: [{ severity: "WARNING", message: "Review this task." }],
          },
        ],
      },
    ];
    configureBatchResponses({
      "first.bpmn": { checkResponseJson: oneFinding, convertedContent: bpmn },
      "empty.bpmn": { checkResponseJson: [], convertedContent: bpmn },
      "last.bpmn": { checkResponseJson: oneFinding, convertedContent: bpmn },
    });
    await uploadAndAnalyze([
      mockFile("first.bpmn", bpmn),
      mockFile("empty.bpmn", bpmn),
      mockFile("last.bpmn", bpmn),
    ]);
    await openUploadedPreview("first.bpmn");

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("1 of 3")).toBeTruthy();
    expect(
      within(dialog).getByRole("button", { name: "Previous file" }).disabled
    ).toBe(true);
    expect(within(dialog).getByRole("button", { name: "Next file" }).disabled).toBe(
      false
    );
    const requestCount = fetchMock.mock.calls.length;

    fireEvent.click(
      within(dialog).getByRole("button", { name: "Next with findings" })
    );

    await screen.findByRole("heading", { name: "Preview: last.bpmn" });
    await waitFor(() =>
      expect(screen.queryByText("Loading preview…")).toBeNull()
    );
    expect(within(dialog).getByText("3 of 3")).toBeTruthy();
    expect(within(dialog).getByRole("button", { name: "Previous file" }).disabled).toBe(
      false
    );
    expect(within(dialog).getByRole("button", { name: "Next file" }).disabled).toBe(
      true
    );
    expect(
      within(dialog).getByRole("button", { name: "Next with findings" }).disabled
    ).toBe(true);
    expect(fetchMock.mock.calls).toHaveLength(requestCount);
  });

  it("preserves the severity filter and clears selection when moving to another file", async () => {
    const bpmn = '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />';
    const responseWithMixedSeverities = (warningId, infoId) => [
      {
        results: [
          {
            elementId: warningId,
            elementType: "bpmn:ServiceTask",
            messages: [{ severity: "WARNING", message: "warning finding" }],
          },
          {
            elementId: infoId,
            elementType: "bpmn:ServiceTask",
            messages: [{ severity: "INFO", message: "info finding" }],
          },
        ],
      },
    ];
    configureBatchResponses({
      "first.bpmn": {
        checkResponseJson: responseWithMixedSeverities("task_1", "info_1"),
        convertedContent: bpmn,
      },
      "second.bpmn": {
        checkResponseJson: responseWithMixedSeverities("task_2", "info_2"),
        convertedContent: bpmn,
      },
    });
    await uploadAndAnalyze([
      mockFile("first.bpmn", bpmn),
      mockFile("second.bpmn", bpmn),
    ]);
    await openUploadedPreview("first.bpmn");
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));

    const firstWarningRow = within(
      screen.getByRole("table", { name: "Findings for this file" })
    ).getByRole("row", { name: /warning finding/ });
    fireEvent.click(
      within(firstWarningRow).getByRole("button", { name: "task_1" })
    );
    expect(firstWarningRow.getAttribute("aria-selected")).toBe("true");

    fireEvent.click(
      screen.getByRole("button", { name: /No action needed INFO \(1\)/ })
    );
    expect(
      screen.getByRole("button", { name: /No action needed INFO \(1\)/ }).getAttribute(
        "aria-pressed"
      )
    ).toBe("false");

    fireEvent.click(screen.getByRole("button", { name: "Next file" }));
    await screen.findByRole("heading", { name: "Preview: second.bpmn" });
    await waitFor(() =>
      expect(screen.queryByText("Loading preview…")).toBeNull()
    );
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(2));

    expect(
      screen.getByRole("button", { name: /No action needed INFO \(1\)/ }).getAttribute(
        "aria-pressed"
      )
    ).toBe("false");
    const secondWarningRow = within(
      screen.getByRole("table", { name: "Findings for this file" })
    ).getByRole("row", { name: /warning finding/ });
    expect(secondWarningRow.getAttribute("aria-selected")).toBe("false");
    expect(screen.queryByText("info finding")).toBeNull();
  });

  it("keeps the position and preview content in sync across BPMN, DMN, and form files", async () => {
    const bpmn = '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="converted" /></definitions>';
    const dmn = '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"><decision id="converted" /></definitions>';
    const form = JSON.stringify({ type: "default", components: [] });
    configureBatchResponses({
      "process.bpmn": { checkResponseJson: [], convertedContent: bpmn },
      "decision.dmn": { checkResponseJson: [], convertedContent: dmn },
      "customer.form": { checkResponseJson: [], convertedContent: form },
    });
    await uploadAndAnalyze([
      mockFile("process.bpmn"),
      mockFile("decision.dmn"),
      mockFile("customer.form"),
    ]);
    await openUploadedPreview("process.bpmn");
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    expect(bpmnMocks.instances[0].importedXml).toEqual([bpmn]);
    expect(screen.getByText("1 of 3")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Next file" }));
    await screen.findByRole("heading", { name: "Preview: decision.dmn" });
    expect(await screen.findByTestId("dmn-preview")).toBeTruthy();
    expect(testState.dmnPreviewProps.at(-1).xml).toBe(dmn);
    expect(screen.getByText("2 of 3")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Next file" }));
    await screen.findByRole("heading", { name: "Preview: customer.form" });
    expect(await screen.findByTestId("form-preview")).toBeTruthy();
    expect(testState.formPreviewProps.at(-1).schema).toEqual({
      type: "default",
      components: [],
    });
    expect(screen.getByText("3 of 3")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Next file" }).disabled).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "Previous file" }));
    await screen.findByRole("heading", { name: "Preview: decision.dmn" });
    expect(testState.dmnPreviewProps.at(-1).xml).toBe(dmn);
    expect(screen.getByText("2 of 3")).toBeTruthy();
  });

  it("navigates through failed files and distinguishes missing analysis from empty results", async () => {
    const bpmn = '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />';
    const finding = [
      {
        results: [
          {
            elementId: "failed_task",
            messages: [{ severity: "TASK", message: "Resolve this task." }],
          },
        ],
      },
    ];
    configureBatchResponses({
      "first.bpmn": { checkResponseJson: [], convertedContent: bpmn },
      "conversion-failed.bpmn": {
        checkResponseJson: finding,
        conversionError: true,
      },
      "analysis-failed.bpmn": {
        analysisError: "Analysis service unavailable.",
      },
    });
    await uploadAndAnalyze([
      mockFile("first.bpmn", bpmn),
      mockFile("conversion-failed.bpmn", bpmn),
      mockFile("analysis-failed.bpmn", bpmn),
    ]);
    await openUploadedPreview("first.bpmn");

    const requestCount = fetchMock.mock.calls.length;
    fireEvent.click(screen.getByRole("button", { name: "Next file" }));
    await screen.findByRole("heading", {
      name: "Preview: conversion-failed.bpmn",
    });
    expect(screen.getByText("2 of 3")).toBeTruthy();
    expect(
      within(screen.getByRole("dialog")).getByRole("alert").textContent
    ).toContain("Conversion failed (HTTP 502)");
    expect(screen.getByText("Resolve this task.")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Next file" }));
    await screen.findByRole("heading", {
      name: "Preview: analysis-failed.bpmn",
    });
    expect(screen.getByText("3 of 3")).toBeTruthy();
    expect(
      within(screen.getByRole("dialog"))
        .getByRole("alert")
        .textContent
    ).toContain("Analysis service unavailable.");
    expect(screen.queryByText("No findings for this file.")).toBeNull();
    expect(screen.getByRole("button", { name: "Next file" }).disabled).toBe(true);
    expect(fetchMock.mock.calls).toHaveLength(requestCount);
  });

  it("refreshes the selected preview when a file's existing result finishes processing", async () => {
    const original =
      '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="original" /></definitions>';
    const converted =
      '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="converted" /></definitions>';
    const secondAnalysis = deferred();
    const secondConversion = deferred();
    const secondFinding = [
      {
        results: [
          {
            elementId: "task_2",
            messages: [{ severity: "WARNING", message: "Finding from the second file." }],
          },
        ],
      },
    ];
    fetchMock.mockImplementation((url, options) => {
      const fileName = options.body.get("file").name;
      if (url.endsWith("/check")) {
        if (fileName === "second.bpmn") return secondAnalysis.promise;
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue([]),
        });
      }
      if (fileName === "second.bpmn") return secondConversion.promise;

      return Promise.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        blob: vi
          .fn()
          .mockResolvedValue(new Blob([fileName === "second.bpmn" ? converted : original])),
      });
    });
    await uploadAndAnalyze([
      mockFile("first.bpmn", original),
      mockFile("second.bpmn", original),
    ]);

    const firstRow = fileRow("first.bpmn");
    await within(firstRow).findByRole("button", { name: "Download first.bpmn" });
    fireEvent.click(
      within(firstRow).getByRole("button", {
        name: "Preview analysis findings for first.bpmn",
      })
    );
    await screen.findByRole("heading", { name: "Preview: first.bpmn" });
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));

    fireEvent.click(screen.getByRole("button", { name: "Next file" }));
    await screen.findByRole("heading", { name: "Preview: second.bpmn" });
    expect(screen.getByText("File analysis is still in progress.")).toBeTruthy();

    secondAnalysis.resolve({
      ok: true,
      headers: { get: vi.fn().mockReturnValue(null) },
      json: vi.fn().mockResolvedValue(secondFinding),
    });

    expect(
      await screen.findByText(
        "Finding from the second file.",
        {},
        { timeout: 10000 }
      )
    ).toBeTruthy();
    await waitFor(() =>
      expect(bpmnMocks.instances.at(-1)?.importedXml).toContain(original)
    );
    await waitFor(() =>
      expect(bpmnMocks.instances.at(-1)?.canvas.zoom).toHaveBeenCalled()
    );
    const secondFindingRow = within(
      screen.getByRole("table", { name: "Findings for this file" })
    ).getByRole("row", { name: /Finding from the second file/ });
    fireEvent.click(
      within(secondFindingRow).getByRole("button", { name: "task_2" })
    );
    expect(secondFindingRow.getAttribute("aria-selected")).toBe("true");
    expect(
      bpmnMocks.instances.at(-1).canvas.addMarker
    ).toHaveBeenCalledWith("task_2", "finding-selected");

    secondConversion.resolve({
      ok: true,
      headers: { get: vi.fn().mockReturnValue(null) },
      blob: vi.fn().mockResolvedValue(new Blob([converted])),
    });

    await waitFor(() =>
      expect(bpmnMocks.instances.at(-1)?.importedXml).toContain(converted)
    );
    const refreshedFindingRow = within(
      screen.getByRole("table", { name: "Findings for this file" })
    ).getByRole("row", { name: /Finding from the second file/ });
    expect(refreshedFindingRow.getAttribute("aria-selected")).toBe("true");
    expect(
      bpmnMocks.instances.at(-1).canvas.addMarker
    ).toHaveBeenCalledWith("task_2", "finding-selected");
    expect(bpmnMocks.instances.at(-1).selection.select).toHaveBeenCalledWith(
      expect.objectContaining({ id: "task_2" })
    );
    expect(screen.getByRole("heading", { name: "Preview: second.bpmn" })).toBeTruthy();
    expect(fetchMock.mock.calls).toHaveLength(4);
  });
});

describe("accessibility", () => {
  it("renders one page h1 followed by section-level h2 headings", () => {
    render(<App />);

    const headings = screen.getAllByRole("heading");
    const h1s = headings.filter((heading) => heading.tagName === "H1");
    expect(h1s).toHaveLength(1);
    expect(h1s[0].textContent).toBe(
      "Camunda Migration Analyzer & Diagram Converter"
    );
    expect(
      screen.getByRole("heading", { level: 2, name: "Add files" })
    ).toBeTruthy();
    expect(
      screen.getByRole("heading", { level: 2, name: /Advanced options/ })
    ).toBeTruthy();
  });

  it("gives the remove-file button an accessible name that includes the filename", () => {
    testState.files.splice(0, testState.files.length, {
      name: "invoice.bpmn",
      text: vi.fn(),
    });

    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    expect(
      screen.getByRole("button", { name: "Remove invoice.bpmn" })
    ).toBeTruthy();
  });

  it("gives the download button an accessible name that includes the filename, once converted", async () => {
    configureUpload({
      fileName: "process.bpmn",
      content:
        '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));
    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);

    expect(
      await screen.findByRole("button", { name: "Download process.bpmn" })
    ).toBeTruthy();
  });
});

describe("target platform version", () => {
  it("labels every supported version and selects the latest stable by default", () => {
    render(<App />);

    const versionGroup = screen.getByRole("radiogroup", {
      name: "Target Camunda 8 version",
    });
    const latestStable = within(versionGroup).getByRole("radio", {
      name: "8.9 Latest stable",
    });
    const earlierStable = within(versionGroup).getByRole("radio", {
      name: "8.8 Earlier stable",
    });
    const nextVersion = within(versionGroup).getByRole("radio", {
      name: "8.10 Next version",
    });

    expect(within(versionGroup).getByText("Latest stable")).toBeTruthy();
    expect(within(earlierStable).getByText("Earlier stable")).toBeDefined();
    expect(within(latestStable).getByText("Latest stable")).toBeDefined();
    expect(within(nextVersion).getByText("Next version")).toBeDefined();
    expect(latestStable.getAttribute("aria-checked")).toBe("true");
    expect(latestStable.getAttribute("tabindex")).toBe("0");
    expect(earlierStable.getAttribute("aria-checked")).toBe("false");
    expect(nextVersion.getAttribute("aria-checked")).toBe("false");
  });

  it("sends an explicitly selected 8.9 target to the conversion endpoints", async () => {
    configureUpload({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });
    render(<App />);

    const versionGroup = screen.getByRole("radiogroup", {
      name: "Target Camunda 8 version",
    });
    fireEvent.click(
      within(versionGroup).getByRole("radio", {
        name: "8.9 Latest stable",
      })
    );
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);
    await screen.findByRole("button", { name: "Download process.bpmn" });

    const conversionRequests = fetchMock.mock.calls.filter(([url]) =>
      url.endsWith("/check") || url.endsWith("/convert")
    );
    expect(conversionRequests).toHaveLength(2);
    conversionRequests.forEach(([, options]) => {
      expect(options.body.get("platformVersion")).toBe("8.9");
    });
  });
});

describe("upload onboarding guidance", () => {
  // The 93-file batch limit mirrors MAX_BATCH_FILES in App.jsx: the server
  // accepts up to 100 multipart parts (server.tomcat.max-part-count), and
  // createFormData() always appends 7 non-file fields (platformVersion + 6
  // config options), leaving 93 parts available for files.
  const MAX_BATCH_FILES = 93;
  const BATCH_FILE_WARNING_THRESHOLD = 84;

  it("states the batch limit and hosted-processing disclosure before any files are uploaded", () => {
    render(<App />);

    expect(
      screen.getByText(new RegExp(`support up to ${MAX_BATCH_FILES} files`, "i"))
    ).toBeTruthy();
    expect(
      screen.getByText(/processed by Camunda's hosted service/i)
    ).toBeTruthy();

    const localConverterLinks = [
      screen.getByRole("link", { name: /run the converter locally/i }),
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

  it("keeps the diagram visible when a finding targets a non-rendered BPMN definition", async () => {
    await openPreview({
      fileName: "example-c7.bpmn",
      content: `<definitions
        xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
        xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
        xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
      >
        <message id="Message_1rhrnqe" name="myMessage" />
        <process id="Process_0c0a05x">
          <serviceTask id="Activity_0kko3uz" name="Connector" />
        </process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_0c0a05x">
            <bpmndi:BPMNShape id="Activity_0kko3uz_di" bpmnElement="Activity_0kko3uz">
              <dc:Bounds x="100" y="100" width="100" height="80" />
            </bpmndi:BPMNShape>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </definitions>`,
      checkResponseJson: [
        {
          results: [
            {
              elementId: "Activity_0kko3uz",
              elementType: "bpmn:ServiceTask",
              elementName: "Connector",
              messages: [{ severity: "WARNING", message: "Review the service task." }],
            },
            {
              elementId: "Message_1rhrnqe",
              elementType: "bpmn:Message",
              elementName: "myMessage",
              messages: [
                {
                  severity: "TASK",
                  message: "Please define a correlation key if the message is used in a message catch event.",
                },
              ],
            },
          ],
        },
      ],
      missingElementIds: ["Message_1rhrnqe"],
    });

    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    expect(document.querySelector("#bpmnDiagram")).toBeTruthy();
    expect(screen.queryByText(/The diagram could not be rendered/)).toBeNull();
    expect(bpmnMocks.instances[0].canvas.addMarker).toHaveBeenCalledWith(
      "Activity_0kko3uz",
      "highlight-warning"
    );
    expect(bpmnMocks.instances[0].canvas.addMarker).not.toHaveBeenCalledWith(
      "Message_1rhrnqe",
      "highlight-task"
    );
    expect(screen.getByText("Message_1rhrnqe")).toBeTruthy();
  });

  it("styles the file-results severity cell by the highest severity, not always warning", async () => {
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

    const resultsTable = await screen.findByRole("table", {
      name: "Batch file results",
    });
    const severityLabel = within(resultsTable).getByText("No action needed");
    expect(severityLabel.closest(".severity-cell").className).toContain(
      "severity-cell-info"
    );
    expect(severityLabel.closest(".severity-cell").className).not.toContain(
      "severity-cell-warning"
    );
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
    const viewer = bpmnMocks.instances[0];
    await waitFor(() =>
      expect(viewer.eventBus.on).toHaveBeenCalledWith(
        "selection.changed",
        expect.any(Function)
      )
    );
    return viewer;
  }

  async function openBpmnPreviewWithPendingConversion() {
    const conversion = deferred();
    const checkResponseJson = [
      {
        results: [
          {
            elementId: "task_1",
            elementType: "bpmn:ServiceTask",
            elementName: "Ship order",
            messages: [{ severity: "WARNING", message: "Review this task." }],
          },
        ],
      },
    ];
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue(checkResponseJson),
        });
      }
      if (url.endsWith("/convert")) return conversion.promise;
      throw new Error(`Unexpected request: ${url}`);
    });

    await uploadAndAnalyze([
      mockFile(
        "process.bpmn",
        '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />'
      ),
    ]);
    const previewButton = await screen.findByRole("button", {
      name: "Preview analysis findings for process.bpmn",
    });
    previewButton.focus();
    fireEvent.click(previewButton);
    await screen.findByRole("heading", { name: "Preview: process.bpmn" });
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));

    const viewer = bpmnMocks.instances[0];
    await waitFor(() =>
      expect(viewer.eventBus.on).toHaveBeenCalledWith(
        "selection.changed",
        expect.any(Function)
      )
    );
    return { viewer, conversion };
  }

  async function completePendingConversion(conversion) {
    await act(async () => {
      conversion.resolve({
        ok: true,
        headers: { get: vi.fn().mockReturnValue(null) },
        blob: vi.fn().mockResolvedValue(
          new Blob([
            '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
          ])
        ),
      });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
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

  it("restores a diagram-first selection after a result update recreates the viewer", async () => {
    const { viewer, conversion } = await openBpmnPreviewWithPendingConversion();
    const elementLink = screen.getByRole("button", { name: "task_1" });

    act(() => viewer.eventBus.fireSelectionChanged([{ id: "task_1" }]));
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("true");

    await completePendingConversion(conversion);
    await waitFor(() => expect(bpmnMocks.instances.length).toBeGreaterThan(1));
    const refreshedViewer = bpmnMocks.instances.at(-1);
    await waitFor(() =>
      expect(refreshedViewer.eventBus.on).toHaveBeenCalledWith(
        "selection.changed",
        expect.any(Function)
      )
    );
    expect(refreshedViewer.selection.select).toHaveBeenCalledWith(
      expect.objectContaining({ id: "task_1" })
    );
  });

  it("does not restore a stale diagram selection after selecting an element without findings", async () => {
    const { viewer, conversion } = await openBpmnPreviewWithPendingConversion();
    const elementLink = screen.getByRole("button", { name: "task_1" });

    fireEvent.click(elementLink);
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("true");
    act(() =>
      viewer.eventBus.fireSelectionChanged([{ id: "element_without_findings" }])
    );
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("false");

    await completePendingConversion(conversion);
    await waitFor(() => expect(bpmnMocks.instances.length).toBeGreaterThan(1));
    const refreshedViewer = bpmnMocks.instances.at(-1);
    await waitFor(() =>
      expect(refreshedViewer.eventBus.on).toHaveBeenCalledWith(
        "selection.changed",
        expect.any(Function)
      )
    );
    expect(refreshedViewer.selection.select).not.toHaveBeenCalled();
  });

  it("keeps rows without a stable element reference as plain, non-interactive text", async () => {
    await openBpmnPreviewWithFindings();

    const table = screen.getByRole("table", { name: "Findings for this file" });
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

  it("keeps element ID buttons operable with the keyboard", async () => {
    const user = userEvent.setup();
    const viewer = await openBpmnPreviewWithFindings();
    const elementLink = screen.getByRole("button", { name: "task_1" });

    elementLink.focus();
    expect(document.activeElement).toBe(elementLink);
    await user.keyboard("{Enter}");

    expect(viewer.selection.select).toHaveBeenCalledWith(
      expect.objectContaining({ id: "task_1" })
    );
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("true");
  });

  it("highlights and scrolls every finding row when its diagram element is selected", async () => {
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
              messages: [
                { severity: "WARNING", message: "Review this task." },
                { severity: "INFO", message: "This task was converted." },
              ],
            },
            {
              elementId: null,
              elementType: "bpmn:Process",
              elementName: null,
              messages: [{ severity: "REVIEW", message: "Review the process." }],
            },
            {
              elementId: "task_without_findings",
              elementType: "bpmn:ServiceTask",
              elementName: "No findings",
              messages: [],
            },
          ],
        },
      ],
    });
    await waitFor(() => expect(bpmnMocks.instances).toHaveLength(1));
    const viewer = bpmnMocks.instances[0];
    await waitFor(() =>
      expect(viewer.eventBus.on).toHaveBeenCalledWith(
        "selection.changed",
        expect.any(Function)
      )
    );

    fireEvent.click(screen.getByRole("button", { name: /No action needed INFO \(1\)/ }));
    const findingsTable = screen.getByRole("table", {
      name: "Findings for this file",
    });
    expect(within(findingsTable).queryByText("This task was converted.")).toBeNull();

    act(() => viewer.eventBus.fireSelectionChanged([{ id: "task_1" }]));

    const rows = within(findingsTable).getAllByRole("row").slice(1);
    const selectedRows = rows.filter((row) =>
      within(row).queryByRole("button", { name: "task_1" })
    );
    expect(selectedRows).toHaveLength(2);
    expect(selectedRows.map((row) => row.getAttribute("aria-selected"))).toEqual([
      "true",
      "true",
    ]);
    expect(within(findingsTable).getByText("This task was converted.")).toBeTruthy();
    expect(
      screen.getByRole("button", { name: /No action needed INFO \(1\)/ }).getAttribute("aria-pressed")
    ).toBe("false");
    expect(
      screen.getByText("Selected element findings remain visible while filters are active.")
    ).toBeTruthy();
    expect(scrollIntoView).toHaveBeenCalledTimes(2);
    expect(scrollIntoView.mock.contexts).toEqual(selectedRows);
    expect(scrollIntoView).toHaveBeenNthCalledWith(1, { block: "nearest" });
    expect(scrollIntoView).toHaveBeenNthCalledWith(2, { block: "nearest" });
    expect(viewer.canvas.addMarker).toHaveBeenCalledWith("task_1", "finding-selected");

    act(() => viewer.eventBus.fireSelectionChanged([{ id: "task_without_findings" }]));

    const remainingTaskRows = within(findingsTable)
      .getAllByRole("row")
      .filter((row) => within(row).queryByRole("button", { name: "task_1" }));
    expect(remainingTaskRows).toHaveLength(1);
    expect(remainingTaskRows[0].getAttribute("aria-selected")).toBe("false");
    expect(viewer.canvas.removeMarker).toHaveBeenCalledWith(
      "task_1",
      "finding-selected"
    );
    expect(viewer.canvas.addMarker).not.toHaveBeenCalledWith(
      "task_without_findings",
      "finding-selected"
    );
  });

  it("clears synchronized row selection when the diagram selection has no stable ID", async () => {
    const viewer = await openBpmnPreviewWithFindings();
    const elementLink = screen.getByRole("button", { name: "task_1" });

    act(() => viewer.eventBus.fireSelectionChanged([{ id: "task_1" }]));
    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("true");

    act(() => viewer.eventBus.fireSelectionChanged([{}]));

    expect(elementLink.closest("tr").getAttribute("aria-selected")).toBe("false");
    expect(viewer.canvas.removeMarker).toHaveBeenCalledWith(
      "task_1",
      "finding-selected"
    );

    fireEvent.click(screen.getByRole("button", { name: "Close" }));

    await waitFor(() =>
      expect(viewer.eventBus.off).toHaveBeenCalledWith(
        "selection.changed",
        expect.any(Function)
      )
    );
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

    await screen.findByTestId("dmn-preview");
    const table = screen.getByRole("table", { name: "Findings for this file" });
    expect(within(table).queryByRole("button", { name: "decision_1" })).toBeNull();
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

    const opener = screen.getByRole("button", {
      name: "Preview analysis findings for process.bpmn",
    });

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

describe("advanced options", () => {
  function renderConfigureStep() {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Show" }));
  }

  it("associates every option and dependent field with plain-language guidance", () => {
    renderConfigureStep();

    const hints = [
      {
        id: "appendDocumentationOnlyTaskAndWarningHint",
        text: "Appends findings with WARNING or TASK severity to the documentation of each BPMN element, so you can act on them in the Modeler. REVIEW and INFO messages are left out.",
      },
      {
        id: "addDataMigrationExecutionListenerHint",
        text: "Adds an execution listener to blank start events so the Camunda 7 Data Migrator can track migrated instances.",
      },
      {
        id: "dataMigrationExecutionListenerJobTypeHint",
        text: 'Job type used by the listener. Available when "Add data migration execution listener" is selected.',
      },
      {
        id: "keepJobTypeBlankHint",
        text: "Leaves the job type empty on converted delegates so you can set it yourself after conversion.",
      },
      {
        id: "alwaysUseDefaultJobTypeHint",
        text: 'Fills every delegate\'s job type with the default value below, for example to route all delegates to one job worker such as the Camunda 7 Adapter. Available when "Keep job type blank" is cleared.',
      },
      {
        id: "defaultJobTypeHint",
        text: 'Job type applied when "Always use default job type" is selected. Available when "Keep job type blank" is cleared.',
      },
    ];

    hints.forEach(({ id, text }) => {
      const hint = document.getElementById(id);
      expect(hint).toBeTruthy();
      expect(hint.textContent.replace(/\s+/g, " ").trim()).toBe(text);
    });

    [
      ["appendDocumentationOnlyTaskAndWarning", "appendDocumentationOnlyTaskAndWarningHint"],
      ["addDataMigrationExecutionListener", "addDataMigrationExecutionListenerHint"],
      ["dataMigrationExecutionListenerJobType", "dataMigrationExecutionListenerJobTypeHint"],
      ["keepJobTypeBlank", "keepJobTypeBlankHint"],
      ["defaultJobTypeEnabled", "alwaysUseDefaultJobTypeHint"],
      ["defaultJobType", "defaultJobTypeHint"],
    ].forEach(([controlId, hintId]) => {
      expect(document.getElementById(controlId).getAttribute("aria-describedby")).toBe(hintId);
    });
  });

  it("sends the task and warning documentation option when selected", async () => {
    configureUpload({
      fileName: "process.bpmn",
      content:
        '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Show" }));
    fireEvent.click(
      document.getElementById("appendDocumentationOnlyTaskAndWarning")
    );
    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));

    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);

    await waitFor(() => {
      const convertCall = fetchMock.mock.calls.find(([url]) =>
        url.endsWith("/convert")
      );
      expect(convertCall).toBeTruthy();
      expect(convertCall[1].body.get("appendDocumentationOnlyTaskAndWarning")).toBe(
        "true"
      );
    });
  });

  it("explains when dependent fields are disabled and how to enable them", () => {
    renderConfigureStep();

    const listenerCheckbox = document.getElementById("addDataMigrationExecutionListener");
    const listenerJobType = document.getElementById("dataMigrationExecutionListenerJobType");
    expect(listenerJobType.disabled).toBe(true);
    fireEvent.click(listenerCheckbox);
    expect(listenerJobType.disabled).toBe(false);

    const keepJobTypeBlank = document.getElementById("keepJobTypeBlank");
    const defaultJobTypeEnabled = document.getElementById("defaultJobTypeEnabled");
    const defaultJobType = document.getElementById("defaultJobType");
    expect(defaultJobTypeEnabled.disabled).toBe(false);
    expect(defaultJobType.disabled).toBe(false);
    fireEvent.click(keepJobTypeBlank);
    expect(defaultJobTypeEnabled.disabled).toBe(true);
    expect(defaultJobType.disabled).toBe(true);
  });
});

describe("voice and tone", () => {
  it("spells out 'for example' in the JSON download hint", async () => {
    configureUpload({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));
    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);

    await screen.findByRole("heading", { name: "Analysis results" });
    const jsonHint = screen.getByText(
      /Machine-readable findings, for example as input for/
    );
    expect(jsonHint.textContent.replace(/\s+/g, " ").trim()).toBe(
      "Machine-readable findings, for example as input for migration tooling."
    );
    expect(jsonHint.textContent).not.toMatch(/e\.g\./);
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

    const row = await screen.findByText("offline.bpmn").then((el) =>
      el.closest(".file-result-row")
    );
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

  it("surfaces a size-specific error for a non-2xx JSON convert response", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/check")) {
        return Promise.resolve({
          ok: true,
          headers: { get: vi.fn().mockReturnValue(null) },
          json: vi.fn().mockResolvedValue([]),
        });
      }

      return Promise.resolve({
        ok: false,
        status: 413,
        headers: { get: vi.fn().mockReturnValue("application/json") },
        text: vi.fn().mockResolvedValue(
          JSON.stringify({ errorCode: "FILE_SIZE_LIMIT_EXCEEDED" })
        ),
      });
    });

    await uploadAndAnalyze([
      { name: "large.bpmn", text: vi.fn().mockResolvedValue("<xml/>") },
    ]);

    const row = fileRow("large.bpmn");
    const alert = await within(row).findByRole("alert");
    expect(alert.textContent).toMatch(
      /uploaded file is too large\. choose a smaller file and try again\./i
    );
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

    const goodRow = await screen.findByText("good.bpmn").then((el) =>
      el.closest(".file-result-row")
    );
    await within(goodRow).findByRole("button", { name: "Download good.bpmn" });

    const badRow = fileRow("bad.bpmn");
    await within(badRow).findByRole("alert");
    const callsBeforeRetry = fetchMock.mock.calls.length;

    fireEvent.click(within(badRow).getByRole("button", { name: "Retry" }));

    await within(badRow).findByRole("button", { name: "Download bad.bpmn" });

    // Retry only re-ran /check + /convert for the failed file.
    expect(fetchMock.mock.calls.length).toBe(callsBeforeRetry + 2);

    // The other, already-completed row was left untouched.
    expect(within(goodRow).getByRole("button", { name: "Download good.bpmn" })).toBeTruthy();
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

    await within(row).findByRole("button", { name: "Download slow.bpmn" });
    expect(within(row).queryByRole("status")).toBeNull();
  });
});
