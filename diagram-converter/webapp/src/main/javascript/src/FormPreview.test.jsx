/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FormPreview from "./FormPreview";

const { FormMock, formInstances } = vi.hoisted(() => {
  const instances = [];

  class MockForm {
    static importError = null;

    constructor(options) {
      this.options = options;
      this.importedSchemas = [];
      this.properties = [];
      this.destroyed = false;
      instances.push(this);
    }

    importSchema(schema) {
      if (MockForm.importError) {
        return Promise.reject(MockForm.importError);
      }
      this.importedSchemas.push(schema);
      return Promise.resolve();
    }

    setProperty(property, value) {
      this.properties.push([property, value]);
    }

    destroy() {
      this.destroyed = true;
    }
  }

  return { FormMock: MockForm, formInstances: instances };
});

vi.mock("@bpmn-io/form-js-viewer", () => ({
  Form: FormMock,
}));

afterEach(() => {
  cleanup();
  FormMock.importError = null;
  formInstances.length = 0;
});

describe("FormPreview", () => {
  it("renders the schema as read-only and destroys the viewer on unmount", async () => {
    expect(FormPreview).toBeDefined();
    const schema = {
      type: "default",
      components: [{ type: "textfield", key: "customerName" }],
    };
    const onError = vi.fn();
    const { container, unmount } = render(
      <FormPreview schema={schema} onError={onError} />
    );

    await waitFor(() => expect(formInstances).toHaveLength(1));
    const form = formInstances[0];

    expect(form.options.container).toBe(
      container.querySelector(".form-preview-container")
    );
    expect(form.importedSchemas).toEqual([schema]);
    expect(form.properties).toEqual([["readOnly", true]]);
    expect(onError).not.toHaveBeenCalled();

    unmount();

    expect(form.destroyed).toBe(true);
  });

  it("reports schema rendering errors", async () => {
    FormMock.importError = new Error("unsupported component");
    const onError = vi.fn();

    render(
      <FormPreview
        schema={{ type: "default", components: [] }}
        onError={onError}
      />
    );

    await waitFor(() =>
      expect(onError).toHaveBeenCalledWith(
        "The form could not be displayed: unsupported component"
      )
    );
  });
});
