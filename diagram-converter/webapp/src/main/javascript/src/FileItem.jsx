/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Loading } from "@carbon/react";

import {
  Download,
  TrashCan,
  View,
  WarningFilled,
  CheckmarkFilled,
} from "@carbon/react/icons";

import Paperclip from "./Paperclip.svg";
import { getSeverityStyleKey } from "./findings";

function statusLabel(isChecked) {
  return isChecked ? "Converting..." : "Analyzing...";
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
  highestSeverity,
}) {
  const severityKey = getSeverityStyleKey(highestSeverity);
  const highestSeverityLabel = highestSeverity || "Unknown";
  const findingCountLabel = `${findingCount} finding${findingCount !== 1 ? "s" : ""}`;

  return (
    <div className="FileItem">
      <div className="FileItemMain">
        <div className="left">
          <img src={Paperclip} />
          <span title={name}>{name}</span>
          {status === "success" && (
            <div style={{ color: "#2ada1e" }}>
              <CheckmarkFilled />
            </div>
          )}
        </div>
        <div className="right">
          {findingCount > 0 && (
            <span
              className={`fileItemFindingCount fileItemFindingCount-${severityKey}`}
              title={`Highest severity: ${highestSeverityLabel}`}
              aria-label={`${findingCountLabel}, highest severity ${highestSeverityLabel}`}
            >
              {severityKey === "info" ? <CheckmarkFilled aria-hidden="true" /> : <WarningFilled aria-hidden="true" />}
              {findingCountLabel}
            </span>
          )}
          {status === "uploading" && (
            <span className="fileItemStatus" role="status">
              <Loading small withOverlay={false} />
              <span className="fileItemStatusLabel">{statusLabel(isChecked)}</span>
            </span>
          )}
          {isChecked && previewAction && (
            <button
              type="button"
              className="download"
              onClick={previewAction}
              title={previewTitle}
              aria-label={previewTitle}
            >
              <View />
            </button>
          )}
          {isConverted && downloadAction && !error && (
            <button
              type="button"
              className="download"
              onClick={downloadAction}
              title={`Download ${name}`}
              aria-label={`Download ${name}`}
            >
              <Download />
            </button>
          )}
          {onDelete && (
            <button
              type="button"
              onClick={onDelete}
              title={`Remove ${name}`}
              aria-label={`Remove ${name}`}
            >
              <TrashCan />
            </button>
          )}
        </div>
      </div>
      {error && (
        <div className="FileItemError" role="alert">
          <WarningFilled aria-hidden="true" className="fileItemErrorIcon" />
          <span className="fileItemErrorText">{error}</span>
          {onRetry && (
            <button type="button" className="fileItemRetry" onClick={onRetry}>
              Retry
            </button>
          )}
        </div>
      )}
    </div>
  );
}
