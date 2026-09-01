/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Download,
  Trash,
  Eye,
  AlertTriangle,
  AlertCircle,
  MessageCircleWarning,
  Info,
  Check,
  Loader2,
  RefreshCw,
} from "lucide-react";

import { getSeverityStyleKey } from "./findings";

function Spinner() {
  return (
    <Loader2
      aria-hidden="true"
      className="size-4 animate-spin text-primary-action-default"
    />
  );
}

// Single, unambiguous in-progress indicator for a file row: one spinner plus
// one status label describing the current phase (analyze, then convert).
function statusLabel(isChecked) {
  return isChecked ? "Converting…" : "Analyzing…";
}

// One icon per severity tier so the badge doesn't rely on color alone to
// tell a warning-heavy file apart from an informational-only one.
const SEVERITY_BADGE_ICON = {
  warning: AlertTriangle,
  task: AlertCircle,
  review: MessageCircleWarning,
  info: Info,
};

export default function FileItem({
  name,
  error,
  status,
  isChecked,
  isConverted,
  downloadAction,
  previewAction,
  previewTitle = "Preview analysis findings",
  onDelete,
  onRetry,
  findingCount,
  highestSeverity,
}) {
  const severityKey = getSeverityStyleKey(highestSeverity);
  const SeverityIcon = SEVERITY_BADGE_ICON[severityKey];
  const highestSeverityLabel = highestSeverity || "Unknown";
  const findingCountLabel = `${findingCount} finding${findingCount !== 1 ? "s" : ""}`;

  return (
    <div className="FileItem">
      <div className="FileItemMain">
        <div className="left">
          {status === "success" && (
            <div className="fileItemCheck">
              <Check />
            </div>
          )}
          <span
            className={isConverted && downloadAction && !error ? "downloadable" : ""}
            onClick={isConverted && downloadAction && !error ? downloadAction : undefined}
          >
            {name}
          </span>
        </div>
        <div className="right">
          {findingCount > 0 && (
            <span
              className={`fileItemFindingCount fileItemFindingCount-${severityKey}`}
              title={`Highest severity: ${highestSeverityLabel}`}
              aria-label={`${findingCountLabel}, highest severity ${highestSeverityLabel}`}
            >
              <SeverityIcon aria-hidden="true" className="fileItemFindingCountIcon" />
              {findingCountLabel}
            </span>
          )}

          {status === "uploading" && (
            <span className="fileItemStatus" role="status">
              <Spinner />
              <span className="fileItemStatusLabel">{statusLabel(isChecked)}</span>
            </span>
          )}
          {isChecked && previewAction && (
            <button
              className="download"
              onClick={previewAction}
              title={previewTitle}
              aria-label={previewTitle}
            >
              <Eye />
            </button>
          )}
          {isConverted && downloadAction && !error && (
            <button className="download" onClick={downloadAction} title="Download converted model" aria-label="Download converted model">
              <Download />
            </button>
          )}
          {onDelete && (
            <button onClick={onDelete}>
              <Trash />
            </button>
          )}
        </div>
      </div>
      {error && (
        <div className="FileItemError" role="alert">
          <AlertTriangle aria-hidden="true" className="fileItemErrorIcon" />
          <span className="fileItemErrorText">{error}</span>
          {onRetry && (
            <button type="button" className="fileItemRetry" onClick={onRetry}>
              <RefreshCw aria-hidden="true" />
              Retry
            </button>
          )}
        </div>
      )}
    </div>
  );
}
