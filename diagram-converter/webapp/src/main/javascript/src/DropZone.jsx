/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import InboxIcon from "./Inbox.svg";

export default function DropZone({ onFiles }) {
  function selectFileToUpload() {
    const input = document.createElement("input");
    input.setAttribute("type", "file");
    input.setAttribute("accept", ".xml, .bpmn, .dmn, .form");
    input.setAttribute("multiple", "true");

    input.addEventListener("change", () => {
      onFiles(input.files);
    });

    input.click();
  }

  function processFile(evt) {
    evt.preventDefault();

    const { files } = evt.dataTransfer;
    if (files) {
      onFiles(files);
    }
  }

  return (
    <div
      className="DropZone"
      role="button"
      tabIndex={0}
      onDragOver={(evt) => evt.preventDefault()}
      onDrop={processFile}
      onClick={selectFileToUpload}
      onKeyDown={(evt) => {
        if (evt.key === "Enter") {
          evt.preventDefault();
          selectFileToUpload();
        } else if (evt.key === " ") {
          // Prevent page scroll; activate on keyup like a native button
          evt.preventDefault();
        }
      }}
      onKeyUp={(evt) => {
        if (evt.key === " ") {
          selectFileToUpload();
        }
      }}
    >
      <img src={InboxIcon} alt="" />
      <h2>Click or drag files here to upload</h2>
      <p>Supports .bpmn, .dmn, .form, and .xml files.</p>
    </div>
  );
}
