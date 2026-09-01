/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Tooltip,
  TooltipTrigger,
  TooltipContent,
  TooltipProvider,
} from "@camunda/design-system";

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
} from "lucide-react";

import { getSeverityStyleKey } from "./findings";

function Spinner() {
  return (
    <Loader2
      aria-label="Loading"
      role="status"
      className="size-4 animate-spin text-primary-action-default"
    />
  );
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
  findingCount,
  highestSeverity,
}) {
  const severityKey = getSeverityStyleKey(highestSeverity);
  const SeverityIcon = SEVERITY_BADGE_ICON[severityKey];
  const highestSeverityLabel = highestSeverity || "Unknown";
  const findingCountLabel = `${findingCount} finding${findingCount !== 1 ? "s" : ""}`;
  return (
    <div className="FileItem">
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

        {error && (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <div
                  tabIndex={0}
                  role="img"
                  aria-label={error}
                  style={{ color: "var(--danger-action-default)" }}
                >
                  <AlertTriangle />
                </div>
              </TooltipTrigger>
              <TooltipContent>{error}</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        )}
        {status === "uploading" && !isChecked && <Spinner />}
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
        {status === "uploading" && !isConverted && <Spinner />}
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
  );
}
