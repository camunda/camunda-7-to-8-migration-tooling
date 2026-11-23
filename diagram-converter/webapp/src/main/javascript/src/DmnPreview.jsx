/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useRef } from "react";

export default function DmnPreview({ xml, onError }) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!xml || !containerRef.current) {
      return;
    }

    let isActive = true;
    let viewer;

    import("dmn-js/lib/Viewer")
      .then(({ default: DmnJS }) => {
        if (!isActive) {
          return;
        }

        viewer = new DmnJS({ container: containerRef.current });
        return viewer.importXML(xml);
      })
      .then(() => {
        if (!isActive) {
          return;
        }

        const canvas = viewer?.getActiveViewer?.()?.get?.("canvas");
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
      viewer?.destroy();
    };
  }, [xml, onError]);

  return <div ref={containerRef} className="diagram-container dmn-preview-container" />;
}
