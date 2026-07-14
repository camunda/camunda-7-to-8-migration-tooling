/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Loading, Tooltip } from "@camunda/design-system/carbon-compat";

import {
  Download,
  Trash,
  Eye,
  AlertTriangle,
  Check,
} from "lucide-react";

export default function FileItem({
  name,
  error,
  status,
  isChecked,
  isConverted,
  downloadAction,
  previewAction,
  onDelete,
  findingCount,
}) {
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
          <span className="fileItemFindingCount">{findingCount} finding{findingCount !== 1 ? 's' : ''}</span>
        )}

        {error && (
          <Tooltip label={error}>
            <div style={{ color: "var(--danger-action-default)" }}>
              <AlertTriangle />
            </div>
          </Tooltip>
        )}
        {status === "uploading" && !isChecked && <Loading small withOverlay={false} />}
        {isChecked && previewAction && (
          <button className="download" onClick={previewAction} title="Preview the analyzer results for this model">
            <Eye />
          </button>
        )}
        {status === "uploading" && !isConverted && <Loading small withOverlay={false} />}
        {isConverted && downloadAction && !error && (
          <button className="download" onClick={downloadAction} title="Download the converted model">
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
