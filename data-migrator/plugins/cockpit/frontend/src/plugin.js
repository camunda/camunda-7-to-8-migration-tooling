/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from "react";
import { createRoot } from "react-dom/client";

import SkippedEntities from "./SkippedEntities.js";

let root = null;
let currentNode = null;

export default {
  id: "camunda-7-to-8-data-migrator",
  pluginPoint: "cockpit.processes.dashboard",
  render: (node, { api }) => {
    if (!root || currentNode !== node) {
      if (root) {
        root.unmount();
      }
      root = createRoot(node);
      currentNode = node;
    }
    root.render(<SkippedEntities camundaAPI={ api } />);
  },
  unmount: () => {
    if (root) {
      root.unmount();
      root = null;
      currentNode = null;
    }
  },

  // make sure we have a higher priority than the default plugin
  priority: 12,
};
