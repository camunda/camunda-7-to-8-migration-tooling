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
      };
      instances.push(this);
    }

    importXML(xml) {
      this.importedXml.push(xml);
      return Promise.resolve();
    }

    get() {
      return this.canvas;
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
  fireEvent.click(previewButton);

  await screen.findByRole("heading", { name: "Preview" });
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

describe("advanced options", () => {
  function renderConfigureStep() {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Advanced options" }));
  }

  it("explains what each advanced option does", () => {
    renderConfigureStep();

    expect(
      screen.getByText(
        /Adds an execution listener to blank start events so the Camunda 7 Data Migrator can track migrated instances\./
      )
    ).toBeTruthy();
    expect(
      screen.getByText(
        /Leaves the job type empty on converted delegates so you can set it yourself after conversion\./
      )
    ).toBeTruthy();
    expect(
      screen.getByText(
        /Fills every delegate's job type with the default value below, for example to route all delegates to one job worker such as the Camunda 7 Adapter\./
      )
    ).toBeTruthy();
  });

  it("explains why the execution listener job type field is disabled and how to enable it", () => {
    renderConfigureStep();

    const jobTypeInput = screen.getByLabelText("Execution listener job type");
    expect(jobTypeInput.disabled).toBe(true);
    expect(
      screen.getByText(
        /Available when "Add data migration execution listener" is selected\./
      )
    ).toBeTruthy();

    fireEvent.click(
      screen.getByRole("checkbox", { name: "Add data migration execution listener" })
    );
    expect(jobTypeInput.disabled).toBe(false);
  });

  it("explains why the always-use-default checkbox and default job type field are disabled and how to enable them", () => {
    renderConfigureStep();

    const alwaysUseDefaultCheckbox = screen.getByRole("checkbox", {
      name: "Always use default job type",
    });
    const defaultJobTypeInput = screen.getByLabelText("Default job type");
    expect(alwaysUseDefaultCheckbox.disabled).toBe(false);
    expect(defaultJobTypeInput.disabled).toBe(false);

    fireEvent.click(screen.getByRole("checkbox", { name: "Keep job type blank" }));

    expect(alwaysUseDefaultCheckbox.disabled).toBe(true);
    expect(defaultJobTypeInput.disabled).toBe(true);
    // Both the checkbox and its dependent text field explain why they are
    // disabled and how to re-enable them.
    expect(
      screen.getAllByText(/Available when "Keep job type blank" is cleared\./)
    ).toHaveLength(2);
  });
});

describe("progress and step numbering", () => {
  it("uses letters for the nested configure steps so they never collide with the top-level step numbers", () => {
    render(<App />);

    // Top-level progress indicator: "Configure" / "Results".
    expect(screen.getByText("Configure")).toBeTruthy();
    expect(screen.getByText("Results")).toBeTruthy();

    // Nested steps inside "Configure" use letters, not digits, so there is
    // never a second, conflicting "1"/"2" alongside the top-level indicator.
    // Scope the assertion to .flowStepNumber elements to avoid false positives
    // from unrelated digits elsewhere in the UI.
    const stepNumbers = document
      .querySelectorAll(".flowStepNumber");
    const stepNumberTexts = Array.from(stepNumbers).map((el) => el.textContent);
    expect(stepNumberTexts).toEqual(["A", "B"]);
  });
});

describe("voice and tone", () => {
  it("does not use 'please' in the download failure message", async () => {
    configureUpload({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));
    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert to Camunda/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);

    const downloadButton = await screen.findByRole("button", {
      name: "Download all converted files as ZIP",
    });
    // Wait for the analyze/convert flow to finish (button enables once a
    // successfully converted file is available) before swapping the fetch
    // mock so we don't intercept the in-flight analyze/convert requests.
    await waitFor(() => expect(downloadButton.disabled).toBe(false));

    fetchMock.mockImplementation(() =>
      Promise.resolve({
        ok: false,
        headers: { get: vi.fn().mockReturnValue(null) },
        json: vi.fn().mockRejectedValue(new Error("not json")),
      })
    );
    fireEvent.click(downloadButton);

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("Download failed. Try again.")).toBeTruthy();
    expect(screen.queryByText(/please/i)).toBeNull();
  });

  it("spells out 'for example' instead of 'e.g.' in the JSON download hint", async () => {
    configureUpload({
      fileName: "process.bpmn",
      content: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
      checkResponseJson: [],
    });
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Upload test file" }));
    const analyzeButton = screen.getByRole("button", {
      name: /Analyze and convert to Camunda/,
    });
    await waitFor(() => expect(analyzeButton.disabled).toBe(false));
    fireEvent.click(analyzeButton);

    expect(
      await screen.findByText(
        /Machine-readable findings, for example as input for AI-assisted migration tooling\./
      )
    ).toBeTruthy();
    expect(screen.queryByText(/e\.g\./)).toBeNull();
  });
});
