/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from "react";
import ReactDOM from "react-dom";

import SkippedEntities from "./SkippedEntities.js";

let container = null;

export default {
  id: "camunda-7-to-8-data-migrator",
  pluginPoint: "cockpit.processes.dashboard",
  render: (node, { api }) => {
    container = node;
    ReactDOM.render(
      <SkippedEntities camundaAPI={ api } />,
      container
    );
  },
  unmount: () => {
    ReactDOM.unmountComponentAtNode(container);
  },

  // make sure we have a higher priority than the default plugin
  priority: 12,
};
