/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.jsx";

const testState = vi.hoisted(() => ({
  files: [],
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

vi.mock("./DmnPreview", () => {
  throw new Error("DMN preview chunk could not be loaded");
});

vi.mock("./FormPreview", () => {
  throw new Error("Form preview chunk could not be loaded");
});

vi.mock("./DropZone", () => ({
  default: ({ onFiles }) => (
    <button type="button" onClick={() => onFiles(testState.files)}>
      Upload test files
    </button>
  ),
}));

const fetchMock = vi.fn();
let originalScrollIntoView;

const fileContent = {
  "decision.dmn":
    '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"><decision id="decision" /></definitions>',
  "customer.form": JSON.stringify({ type: "default", components: [] }),
};

function configureResponses() {
  fetchMock.mockImplementation((url, options) => {
    const fileName = options.body.get("file").name;
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
      blob: vi.fn().mockResolvedValue(new Blob([fileContent[fileName]])),
    });
  });
}

async function uploadAndAnalyze(files) {
  testState.files.splice(0, testState.files.length, ...files);
  configureResponses();
  render(<App />);

  fireEvent.click(screen.getByRole("button", { name: "Upload test files" }));

  const analyzeButton = screen.getByRole("button", {
    name: /Analyze and convert/,
  });
  await waitFor(() => expect(analyzeButton.disabled).toBe(false));
  fireEvent.click(analyzeButton);

  for (const file of files) {
    await screen.findByRole("button", { name: `Download ${file.name}` });
  }
}

function mockFile(name) {
  return new File([fileContent[name]], name);
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  testState.files.length = 0;
  originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
  HTMLElement.prototype.scrollIntoView = vi.fn();
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  if (originalScrollIntoView) {
    HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
  } else {
    delete HTMLElement.prototype.scrollIntoView;
  }
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("lazy preview import failures", () => {
  it.each([
    {
      fileName: "decision.dmn",
      previewButton: "Preview analysis findings for decision.dmn",
      previewTitle: "DMN preview unavailable",
      previewDescription:
        "The DMN preview could not be loaded. Reload the page to try again.",
      position: "1 of 2",
    },
    {
      fileName: "customer.form",
      previewButton: "Preview form for customer.form",
      previewTitle: "Form preview unavailable",
      previewDescription:
        "The form preview could not be loaded. Reload the page to try again.",
      position: "2 of 2",
    },
  ])(
    "keeps the uploaded batch when the $fileName preview import fails",
    async ({
      fileName,
      previewButton,
      previewTitle,
      previewDescription,
      position,
    }) => {
      await uploadAndAnalyze([
        mockFile("decision.dmn"),
        mockFile("customer.form"),
      ]);

      const button = await screen.findByRole("button", {
        name: previewButton,
      });
      button.focus();
      fireEvent.click(button);

      await screen.findByRole("heading", { name: `Preview: ${fileName}` });
      const unavailableAlert = await screen.findByRole("alert");
      expect(within(unavailableAlert).getByText(previewTitle)).toBeTruthy();
      expect(
        within(unavailableAlert).getByText(previewDescription)
      ).toBeTruthy();
      expect(screen.queryByText("Loading preview…")).toBeNull();
      expect(screen.getByText(position)).toBeTruthy();

      fireEvent.click(screen.getByRole("button", { name: "Close" }));

      expect(
        await screen.findByRole("button", { name: "Download decision.dmn" })
      ).toBeTruthy();
      expect(
        screen.getByRole("button", { name: "Download customer.form" })
      ).toBeTruthy();
      expect(
        screen.getByRole("button", {
          name: "Preview analysis findings for decision.dmn",
        })
      ).toBeTruthy();
      expect(
        screen.getByRole("button", { name: "Preview form for customer.form" })
      ).toBeTruthy();
    }
  );
});
