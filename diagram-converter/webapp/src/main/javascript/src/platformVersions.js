/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
const VERSION_HINTS = Object.freeze({
  PREVIOUS_STABLE: "Previous stable",
  LATEST_STABLE: "Latest stable",
  NEXT: "Next version",
});

// Keep this list independent of the Camunda libraries used to build a release
// line. The converter accepts the target platform version as an input.
export const SUPPORTED_PLATFORM_VERSIONS = Object.freeze([
  { value: "8.8", label: "8.8", hint: VERSION_HINTS.PREVIOUS_STABLE },
  { value: "8.9", label: "8.9", hint: VERSION_HINTS.LATEST_STABLE },
  { value: "8.10", label: "8.10", hint: VERSION_HINTS.NEXT },
]);

const latestStableVersions = SUPPORTED_PLATFORM_VERSIONS.filter(
  ({ hint }) => hint === VERSION_HINTS.LATEST_STABLE
);

if (latestStableVersions.length !== 1) {
  throw new Error(
    "The target version list must contain exactly one latest stable version."
  );
}

export const DEFAULT_PLATFORM_VERSION = latestStableVersions[0].value;
