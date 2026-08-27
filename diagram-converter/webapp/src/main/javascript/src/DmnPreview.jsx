/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useRef } from "react";
import DmnJS from "dmn-js";

export default function DmnPreview({ xml, onError }) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!xml || !containerRef.current) {
      return;
    }

    const viewer = new DmnJS({ container: containerRef.current });
    let isActive = true;

    viewer
      .importXML(xml)
      .then(() => {
        if (!isActive) {
          return;
        }

        const canvas = viewer.getActiveViewer?.()?.get?.("canvas");
        canvas?.zoom?.("fit-viewport");
      })
      .catch((error) => {
        if (isActive) {
          const message = error instanceof Error ? error.message : "Unknown rendering error";
          onError(`Unable to render this DMN model: ${message}`);
        }
      });

    return () => {
      isActive = false;
      viewer.destroy();
    };
  }, [xml, onError]);

  return <div ref={containerRef} className="diagram-container dmn-preview-container" />;
}
