/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { getSeverityInfo, getSeverityStyleKey } from "./findings";

export default function SeverityCell({ severity, showActionLabel = false }) {
  const normalizedSeverity = severity || "Unknown";
  const { actionLabel, label } = getSeverityInfo(normalizedSeverity);
  const styleKey = getSeverityStyleKey(normalizedSeverity);
  const displayLabel =
    showActionLabel && actionLabel && actionLabel !== label
      ? `${actionLabel}: ${label}`
      : label;

  return (
    <span
      className={`severity-cell severity-cell-${styleKey}`}
      data-severity={normalizedSeverity}
    >
      <span className="severity-cell-label">{displayLabel}</span>
      <span className="severity-cell-code"> ({normalizedSeverity})</span>
    </span>
  );
}
