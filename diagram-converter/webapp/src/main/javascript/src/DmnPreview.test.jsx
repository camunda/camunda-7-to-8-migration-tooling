/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import DmnPreview from "./DmnPreview";

const { DmnJSMock, viewerInstances } = vi.hoisted(() => {
  const instances = [];

  class MockDmnJS {
    static importError = null;

    constructor(options) {
      this.options = options;
      this.importedXml = [];
      this.destroyed = false;
      this.canvas = { zoom: vi.fn() };
      instances.push(this);
    }

    importXML(xml) {
      if (MockDmnJS.importError) {
        return Promise.reject(MockDmnJS.importError);
      }

      this.importedXml.push(xml);
      return Promise.resolve();
    }

    getActiveViewer() {
      return {
        get: () => this.canvas,
      };
    }

    destroy() {
      this.destroyed = true;
    }
  }

  return { DmnJSMock: MockDmnJS, viewerInstances: instances };
});

vi.mock("dmn-js", () => ({
  default: DmnJSMock,
}));

afterEach(() => {
  cleanup();
  DmnJSMock.importError = null;
  viewerInstances.length = 0;
});

describe("DmnPreview", () => {
  it("renders the DMN XML, fits the diagram, and destroys the viewer on unmount", async () => {
    const xml = '<definitions id="definitions" xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" />';
    const onError = vi.fn();
    const { container, unmount } = render(<DmnPreview xml={xml} onError={onError} />);

    await waitFor(() => expect(viewerInstances).toHaveLength(1));
    const viewer = viewerInstances[0];

    expect(viewer.options.container).toBe(
      container.querySelector(".dmn-preview-container")
    );
    expect(viewer.importedXml).toEqual([xml]);
    expect(viewer.canvas.zoom).toHaveBeenCalledWith("fit-viewport");
    expect(onError).not.toHaveBeenCalled();

    unmount();

    expect(viewer.destroyed).toBe(true);
  });

  it("reports DMN rendering errors", async () => {
    DmnJSMock.importError = new Error("unsupported DMN version");
    const onError = vi.fn();

    render(
      <DmnPreview
        xml="<definitions />"
        onError={onError}
      />
    );

    await waitFor(() =>
      expect(onError).toHaveBeenCalledWith(
        "Unable to render this DMN model: unsupported DMN version"
      )
    );
  });
});
