/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Loading, Tooltip } from "@carbon/react";

import {
  Download,
  TrashCan,
  View,
  WarningFilled,
  CheckmarkFilled,
} from "@carbon/react/icons";

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
            <CheckmarkFilled />
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
            <div style={{ color: "#da1e28" }}>
              <WarningFilled />
            </div>
          </Tooltip>
        )}
        {status === "uploading" && !isChecked && <Loading small withOverlay={false} />}
        {isChecked && previewAction && (
          <button className="download" onClick={previewAction} title="Preview the analyzer results for this model">
            <View />
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
            <TrashCan />
          </button>
        )}
      </div>
    </div>
  );
}
