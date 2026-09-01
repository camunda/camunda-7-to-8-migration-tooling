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
  Check,
  Loader2,
  RefreshCw,
} from "lucide-react";

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
}) {
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
            <span className="fileItemFindingCount">{findingCount} finding{findingCount !== 1 ? 's' : ''}</span>
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
