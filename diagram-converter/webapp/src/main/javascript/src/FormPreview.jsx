/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useRef } from "react";
import { Form } from "@bpmn-io/form-js-viewer";

export default function FormPreview({ schema, onError }) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!schema || !containerRef.current) {
      return;
    }

    const form = new Form({ container: containerRef.current });
    let isActive = true;

    form
      .importSchema(schema)
      .then(() => {
        if (isActive) {
          form.setProperty("readOnly", true);
        }
      })
      .catch((error) => {
        if (isActive) {
          const message = error instanceof Error ? error.message : "Unknown rendering error";
          onError(`The form could not be displayed: ${message}`);
        }
      });

    return () => {
      isActive = false;
      form.destroy();
    };
  }, [schema, onError]);

  return <div ref={containerRef} className="form-preview-container" />;
}
