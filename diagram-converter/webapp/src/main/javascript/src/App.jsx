/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState, useEffect, useLayoutEffect, useRef } from "react";

import {
  ProgressIndicator,
  ProgressStep,
  Button,
  Form,
  FormGroup,
  Checkbox,
  TextInput,
} from "@carbon/react";

import { Download, Launch, Close, Settings } from "@carbon/react/icons";
import DropZone from "./DropZone";
import FileItem from "./FileItem";
import {
  FINDINGS_TABLE_HEADER,
  buildFindingsRows,
  getHighestSeverity,
  getSeverityStyleKey,
} from "./findings";
import FindingsSection from "./FindingsSection";
import BpmnJS from 'bpmn-js';
import FormPreview from "./FormPreview";
import { parseFormSchema } from "./formSchema";
import {
  DEFAULT_PLATFORM_VERSION,
  getPlatformVersionAriaLabel,
  SUPPORTED_PLATFORM_VERSIONS,
} from "./platformVersions";

// Combined batch actions (ZIP download, XLSX/CSV/JSON analysis export) send
// every uploaded file plus the config fields in a single multipart request.
// The server accepts at most MAX_MULTIPART_PARTS parts total (mirrors
// server.tomcat.max-part-count in application.yaml, which is where the
// server-side FILE_COUNT_LIMIT_EXCEEDED error originates; keep the two in
// sync if that value ever changes). createFormData() always appends
// FIXED_FORM_FIELD_COUNT non-file fields (platformVersion + the 6 config
// options), so the actual per-batch file limit is lower than the raw part
// count.
const MAX_MULTIPART_PARTS = 100;
const FIXED_FORM_FIELD_COUNT = 7;
const MAX_BATCH_FILES = MAX_MULTIPART_PARTS - FIXED_FORM_FIELD_COUNT;
// Warn a bit before the hard limit so users can trim the batch (or switch to
// the local converter) before a combined download fails outright.
const BATCH_FILE_WARNING_THRESHOLD = Math.round(MAX_BATCH_FILES * 0.9);

const LOCAL_CONVERTER_DOCS_URL =
  "https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/#local-web-application";
function App() {
  const baseUrl = ""; // Change this to "http://localhost:8080" if you want to play with it locally by using npm run dev

  const [step, setStep] = useState(0);
  const [files, setFiles] = useState([]);
  const [fileResults, setFileResults] = useState([]);
  const [validFiles, setValidFiles] = useState([]);
  const isSaaS = window.location.hostname !== "localhost";
  const [platformVersion, setPlatformVersion] = useState(DEFAULT_PLATFORM_VERSION);

  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [previewType, setPreviewType] = useState(null);
  const [previewbpmnXml, setPreviewbpmnXml] = useState("");
  const [previewFormSchema, setPreviewFormSchema] = useState(null);
  const [previewFormError, setPreviewFormError] = useState("");
  const [previewDiagramError, setPreviewDiagramError] = useState(false);
  const [previewCheckJson, setPreviewCheckJson] = useState([]);
  const [previewFileName, setPreviewFileName] = useState("");
  const [selectedFindingElementId, setSelectedFindingElementId] = useState(null);

  const [previewTableHeader, setPreviewTableHeader] = useState([]);
  const [previewTableRows, setPreviewTableRows] = useState([]);

  const [showConfig, setShowConfig] = useState(false);
  const versionSegmentedRef = useRef(null);
  const bpmnPreviewRef = useRef(null);
  const bpmnViewerRef = useRef(null);
  const selectedMarkerElementIdRef = useRef(null);
  const previewDialogRef = useRef(null);
  const [configOptions, setConfigOptions] = useState({
    defaultJobType: "camunda-7-job",
    keepJobTypeBlank: false,
    alwaysUseDefaultJobType: false,
    addDataMigrationExecutionListener: false,
    dataMigrationExecutionListenerJobType: "=if legacyId != null then \"migrator\" else \"noop\"",
    appendDocumentationOnlyTaskAndWarning: false,
  });

  function handleVersionKeyDown(event) {
    const navigationalKeys = [
      "ArrowRight",
      "ArrowDown",
      "ArrowLeft",
      "ArrowUp",
      "Home",
      "End",
    ];
    if (!navigationalKeys.includes(event.key)) return;

    event.preventDefault();
    const currentIndex = SUPPORTED_PLATFORM_VERSIONS.findIndex(
      (version) => version.value === platformVersion
    );
    let nextIndex = currentIndex;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") {
      nextIndex = (currentIndex + 1) % SUPPORTED_PLATFORM_VERSIONS.length;
    } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
      nextIndex =
        (currentIndex - 1 + SUPPORTED_PLATFORM_VERSIONS.length) %
        SUPPORTED_PLATFORM_VERSIONS.length;
    } else if (event.key === "Home") {
      nextIndex = 0;
    } else if (event.key === "End") {
      nextIndex = SUPPORTED_PLATFORM_VERSIONS.length - 1;
    }

    setPlatformVersion(SUPPORTED_PLATFORM_VERSIONS[nextIndex].value);
    versionSegmentedRef.current?.querySelectorAll("button")[nextIndex]?.focus();
  }

  useEffect(() => {
      if (!isPreviewOpen || previewType !== "bpmn" || previewDiagramError || !previewbpmnXml) return;

      const viewer = new BpmnJS({ container: bpmnPreviewRef.current });
      let isActive = true;
      viewer.importXML(previewbpmnXml).then(() => {
        if (!isActive) return;
        const canvas = viewer.get('canvas');
        canvas.zoom('fit-viewport');

        const elementsWithMessages =
          (Array.isArray(previewCheckJson) ? previewCheckJson : [])
            .flatMap((item) => (Array.isArray(item?.results) ? item.results : []))
            .filter((el) => Array.isArray(el?.messages) && el.messages.length > 0);

        elementsWithMessages.forEach((el) => {
          if (el.elementId) {
            const severityStyleKey = getSeverityStyleKey(getHighestSeverity(el.messages));
            canvas.addMarker(el.elementId, `highlight-${severityStyleKey}`);
          }
        });

        bpmnViewerRef.current = viewer;
      }).catch((error) => {
        if (isActive) {
          console.error("Unable to render BPMN preview:", error);
          setPreviewDiagramError(true);
        }
      });

      return () => {
        isActive = false;
        bpmnViewerRef.current = null;
        selectedMarkerElementIdRef.current = null;
        viewer.destroy();
      };
    }, [isPreviewOpen, previewType, previewDiagramError, previewbpmnXml, previewCheckJson]);

  function selectFindingElement(elementId) {
    if (!elementId || elementId === '-') return;
    const viewer = bpmnViewerRef.current;
    if (!viewer) return;

    try {
      const element = viewer.get('elementRegistry').get(elementId);
      if (!element) return;

      const canvas = viewer.get('canvas');
      if (selectedMarkerElementIdRef.current &&
        selectedMarkerElementIdRef.current !== elementId) {
        canvas.removeMarker(selectedMarkerElementIdRef.current, 'finding-selected');
      }
      canvas.addMarker(elementId, 'finding-selected');
      selectedMarkerElementIdRef.current = elementId;
      canvas.scrollToElement(element);
      viewer.get('selection').select(element);
      setSelectedFindingElementId(elementId);
    } catch (error) {
      console.error("Unable to locate finding element in the diagram:", error);
    }
  }

  useLayoutEffect(() => {
    if (!isPreviewOpen) return undefined;

    const dialogEl = previewDialogRef.current;
    const opener = document.activeElement;
    const previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusableSelector =
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const getFocusable = () =>
      dialogEl ? Array.from(dialogEl.querySelectorAll(focusableSelector)) : [];

    (getFocusable()[0] || dialogEl)?.focus();

    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        e.stopPropagation();
        setIsPreviewOpen(false);
        return;
      }
      if (e.key !== 'Tab' || !dialogEl) return;
      const items = getFocusable();
      if (items.length === 0) {
        e.preventDefault();
        dialogEl.focus();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (e.shiftKey && (active === first || !dialogEl.contains(active))) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && (active === last || !dialogEl.contains(active))) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown, true);
    return () => {
      document.removeEventListener('keydown', handleKeyDown, true);
      document.body.style.overflow = previousBodyOverflow;
      if (opener instanceof HTMLElement && document.contains(opener)) opener.focus();
    };
  }, [isPreviewOpen]);

  function createFormData(files) {
    const formData = new FormData();

    // Normalize to an array (you can pass a single file or an array of files)
    const fileArray = Array.isArray(files) ? files : [files];

    fileArray.forEach((file) => {
      // Append each file, optionally using indexed keys if needed
      formData.append("file", file);
    });

    // Send the selected target version on every request that uses this form data.
    if (platformVersion) formData.append("platformVersion", platformVersion);

    if (configOptions.defaultJobType !== undefined)
      formData.append("defaultJobType", configOptions.defaultJobType);

    if (configOptions.keepJobTypeBlank !== undefined)
      formData.append("keepJobTypeBlank", configOptions.keepJobTypeBlank);

    if (configOptions.alwaysUseDefaultJobType !== undefined)
      formData.append("alwaysUseDefaultJobType", configOptions.alwaysUseDefaultJobType);

    if (configOptions.addDataMigrationExecutionListener !== undefined)
      formData.append("addDataMigrationExecutionListener", configOptions.addDataMigrationExecutionListener);

    if (configOptions.dataMigrationExecutionListenerJobType !== undefined)
      formData.append("dataMigrationExecutionListenerJobType", configOptions.dataMigrationExecutionListenerJobType);
    if (configOptions.appendDocumentationOnlyTaskAndWarning !== undefined)
      formData.append(
        "appendDocumentationOnlyTaskAndWarning",
        configOptions.appendDocumentationOnlyTaskAndWarning
      );
    return formData;
  }

  function updateFileResult(idx, result) {
    setFileResults((prevResults) => {
      const updated = [...prevResults];
      updated[idx] = result;
      return updated;
    });
  }

  // Plain-language message for a failed fetch() call itself (offline, DNS
  // failure, CORS, connection reset, etc.) as opposed to a non-2xx response,
  // which is handled by responseErrorMessage.
  function networkErrorMessage() {
    return "Could not reach the server. Check your connection and try again.";
  }

  // Runs analyze (/check) and convert (/convert) for a single file, updating
  // fileResults as each phase completes. Used both for the initial batch
  // upload and for retrying a single failed file, so failures never affect
  // sibling rows and a retry only reprocesses the file it targets.
  async function processFile(file, idx) {
    const formData = createFormData(file);

    let originalModelXml;
    try {
      originalModelXml = await file.text();
    } catch {
      const result = {
        status: "error",
        errorMessage: "The file could not be read. Please try again.",
        originalModelXml: "",
        checkResponseJson: null,
      };
      updateFileResult(idx, result);
      return result;
    }

    let checkResponse;
    try {
      checkResponse = await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
           "Accept": "application/json"
        },
      });
    } catch {
      const result = {
        status: "error",
        errorMessage: networkErrorMessage(),
        originalModelXml: originalModelXml,
        checkResponseJson: null,
      };
      updateFileResult(idx, result);
      return result;
    }

    if (!checkResponse.ok) {
      const result = {
        status: "error",
        errorMessage: await responseErrorMessage(
          checkResponse,
          `Analysis failed (HTTP ${checkResponse.status})`
        ),
        originalModelXml: originalModelXml,
        checkResponseJson: null,
      };
      updateFileResult(idx, result);
      return result;
    }

    let checkResponseJson;
    try {
      checkResponseJson = await checkResponse.json();
    } catch {
      const result = {
        status: "error",
        errorMessage: "The analysis response could not be read. Please try again.",
        originalModelXml: originalModelXml,
        checkResponseJson: null,
      };
      updateFileResult(idx, result);
      return result;
    }

    let result = {
      status: "uploading",
      originalModelXml: originalModelXml,
      checkResponseJson: checkResponseJson,
    };
    updateFileResult(idx, result);

    let convertResponse;
    try {
      convertResponse = await fetch(baseUrl + "/convert", {
        body: formData,
        method: "POST",
      });
    } catch {
      result = {
        status: "error",
        errorMessage: networkErrorMessage(),
        originalModelXml: originalModelXml,
        checkResponseJson: checkResponseJson,
      };
      updateFileResult(idx, result);
      return result;
    }

    // Extract filename from the Content-Disposition header
    let filename = "downloaded-model.bpmn"; // Default filename

    const contentDisposition = convertResponse.headers.get("Content-Disposition");
    if (contentDisposition) {
      const match = contentDisposition.match(
          /filename\*?=(?:UTF-8'')?["']?([^"';]*)["']?/i
      );
      if (match) {
        filename = decodeURIComponent(match[1]); // Decode if necessary
      }
    }

    if (!convertResponse.ok) {
      result = {
        status: "error",
        errorMessage: await responseErrorMessage(
          convertResponse,
          `Conversion failed (HTTP ${convertResponse.status})`
        ),
        originalModelXml: originalModelXml,
        checkResponseJson: checkResponseJson,
      };
      updateFileResult(idx, result);
      return result;
    }

    let blob;
    try {
      blob = await convertResponse.blob();
    } catch {
      result = {
        status: "error",
        errorMessage: "The converted file could not be read. Please try again.",
        originalModelXml: originalModelXml,
        checkResponseJson: checkResponseJson,
      };
      updateFileResult(idx, result);
      return result;
    }

    result = {
      status: "success",
      originalModelXml: originalModelXml,
      checkResponseJson: checkResponseJson,
      convertedFileBlob: blob,
      filename
    };

    updateFileResult(idx, result);
    return result;
  }

  async function analyzeAndConvert() {
    setStep(2);
    setFileResults(files.map(() => ({ status: "uploading" })));

    const uploadResults = await Promise.all(
      files.map((file, idx) => processFile(file, idx))
    );

    const newValidFiles = files.filter(
      (_, idx) => uploadResults[idx].status === "success"
    );
    setValidFiles(newValidFiles);
  }

  // Reprocesses only the file at `idx`. Other rows (completed or failed) are
  // left untouched, and the ZIP/report downloads only ever see files whose
  // latest result is a success.
  async function retryFile(idx) {
    const file = files[idx];
    updateFileResult(idx, { status: "uploading" });

    const result = await processFile(file, idx);

    setValidFiles((prevValidFiles) => {
      const withoutFile = prevValidFiles.filter((f) => f !== file);
      return result.status === "success" ? [...withoutFile, file] : withoutFile;
    });
  }

  // Returns to the configure step without discarding the uploaded files or
  // their previous results, so users can adjust options and re-run without
  // re-uploading.
  function backToConfigure() {
    setStep(0);
  }

  // Starts a fresh batch: clears the uploaded files, all per-file results and
  // any lingering download error before returning to the configure step.
  function startNewBatch() {
    setFiles([]);
    setFileResults([]);
    setValidFiles([]);
    setStep(0);
  }

  async function responseErrorMessage(response, fallback) {
    const message = (await response.text()).trim();
    if (!message) return fallback;

    const contentType = response.headers.get("Content-Type") || "";
    if (!contentType.includes("application/json")) return message;

    try {
      const errorBody = JSON.parse(message);
      switch (errorBody.errorCode) {
        case "FILE_COUNT_LIMIT_EXCEEDED":
          return "Too many files at once. Remove some files and try again.";
        case "MULTIPART_ERROR":
          return "The uploaded files could not be processed.";
        default:
          return fallback;
      }
    } catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
      return message;
    }
  }

  async function downloadXLS() {
    const formData = createFormData(validFiles);
    //validFiles.forEach((file) => formData.append("file", file));
    await download1("analysis.xlsx",
      await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
          Accept:
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        },
      })
    );
  }
  async function downloadCSV() {
    const formData = createFormData(validFiles);
    //validFiles.forEach((file) => formData.append("file", file));
    await download1("analysis.csv",
      await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
          Accept: "text/csv",
        },
      })
    );
  }
  async function downloadJSON() {
    const formData = createFormData(validFiles);
    await download1("analysis-results.json",
      await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
          Accept: "application/vnd.camunda.analysis+json",
        },
      })
    );
  }
  async function downloadZIP() {
    const formData = createFormData(validFiles);
    //validFiles.forEach((file) => formData.append("file", file));
    await download1("converted-models.zip",
      await fetch(baseUrl + "/convertBatch", {
        body: formData,
        method: "POST",
      })
    );
  }

  async function preview(response, fileName = "") {
    if (!response?.checkResponseJson) return;

    setPreviewTableHeader(FINDINGS_TABLE_HEADER);
    setPreviewTableRows(buildFindingsRows(response.checkResponseJson));

    setPreviewCheckJson(response.checkResponseJson);
    const isBpmn =
      typeof response.originalModelXml === "string" &&
      response.originalModelXml.includes("omg.org/spec/BPMN");
    const modelXml =
      isBpmn && response?.convertedFileBlob
        ? await response.convertedFileBlob.text()
        : response.originalModelXml;
    setPreviewbpmnXml(modelXml);
    setPreviewFormSchema(null);
    setPreviewFormError("");
    setPreviewDiagramError(false);
    setPreviewFileName(fileName);
    setSelectedFindingElementId(null);
    // BPMN is detected by content, not extension: the dropzone also accepts
    // .xml files, which can be BPMN (or DMN) models.
    setPreviewType(
      typeof response.originalModelXml === "string" &&
      response.originalModelXml.includes("omg.org/spec/BPMN")
        ? "bpmn"
        : "other"
    );

    setIsPreviewOpen(true);
  }

  function openFormPreview(schema, errorMessage = "", fileName = "") {
    setPreviewFormSchema(schema);
    setPreviewFormError(errorMessage);
    setPreviewbpmnXml("");
    setPreviewCheckJson([]);
    setPreviewTableHeader([]);
    setPreviewTableRows([]);
    setPreviewDiagramError(false);
    setPreviewFileName(fileName);
    setSelectedFindingElementId(null);
    setPreviewType("form");
    setIsPreviewOpen(true);
  }

  async function previewForm(response, fileName = "") {
    const formContent = response?.convertedFileBlob
      ? await response.convertedFileBlob.text()
      : response?.originalModelXml;
    const { schema, error } = parseFormSchema(formContent);
    openFormPreview(schema, error, fileName);
    setPreviewCheckJson(response?.checkResponseJson || []);
    setPreviewTableHeader(FINDINGS_TABLE_HEADER);
    setPreviewTableRows(buildFindingsRows(response?.checkResponseJson));
  }

  async function download(response) {
    let filename = response.filename;
    let blob = response.convertedFileBlob;
    doDownload(filename, blob);
  }

  async function download1(filename, response) {
    doDownload(filename, await response.blob());
  }

  async function doDownload(filename, blob) {
    const url = URL.createObjectURL(blob);

    // Create and trigger download link
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);

    // Clean up the object URL
    URL.revokeObjectURL(url);
  }

  return (
    <div className="container">
      <div className="pageContent" inert={isPreviewOpen}>
      <div className="whiteBox">
        <div>
          <div>
            <h1>Camunda Migration Analyzer &amp; Diagram Converter</h1>
            <p>
              Convert BPMN, DMN, and Camunda Form files to Camunda 8 and see what needs attention.
            </p>
            <p>
              <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/"
                rel="noopener noreferrer" target="_blank">
                Documentation
              </a>
            </p>
            {!isSaaS && (
              <div>
                <p>
                  <a href="https://diagram-converter.camunda.io">
                    Open online version
                  </a>
                </p>
              </div>
            )}
            {isSaaS && (
              <div>
                <p>
                  <a href="https://github.com/camunda/camunda-7-to-8-migration-tooling/releases">
                    Download local version
                  </a>
                </p>
                <p>
                  <a href="https://legal.camunda.com/licensing-and-other-legal-terms#trial-and-free">
                    Legal &amp; privacy
                  </a>
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
      <div className="whiteBox centered">
        <div className="progressindicators">
          <ProgressIndicator spaceEqually>
            <ProgressStep
              current={step < 2}
              complete={step > 1}
              label="Configure"
            />
            <ProgressStep
              current={step === 2}
              complete={step > 2}
              label="Results"
            />
          </ProgressIndicator>
        </div>


        {step === 0 && (
          <>
            <section>
              <h2>Add files</h2>
              <p>
                Upload BPMN, DMN, or Camunda Form files to analyze and convert.
              </p>
              <p className="uploadGuidance">
                Batch actions (ZIP download, XLSX/CSV/JSON reports) support up to {MAX_BATCH_FILES} files.
                Files are processed by Camunda&apos;s hosted service. To convert more files or keep
                sensitive models private,{" "}
                <a href={LOCAL_CONVERTER_DOCS_URL} target="_blank" rel="noopener noreferrer">
                  run the converter locally
                </a>.
              </p>
            </section>
            <div className="fileUploadBox">
              <DropZone
                onFiles={(files) => {
                  setFiles((prevFiles) => [...prevFiles, ...files]);
                }}
              />
              {files.length >= BATCH_FILE_WARNING_THRESHOLD && (
                <div className="uploadLimitNotice" role="alert">
                  <strong>
                    {files.length > MAX_BATCH_FILES
                      ? `Batch limit exceeded (${MAX_BATCH_FILES} max, ${files.length} added)`
                      : files.length === MAX_BATCH_FILES
                      ? `Batch limit reached (${MAX_BATCH_FILES} files)`
                      : `Approaching the batch limit (${files.length} of ${MAX_BATCH_FILES} files)`}
                  </strong>
                  <p>
                    Combined ZIP and analysis-report downloads support up to{" "}
                    {MAX_BATCH_FILES} files. Remove some files, or{" "}
                    <a href={LOCAL_CONVERTER_DOCS_URL} target="_blank" rel="noopener noreferrer">
                      run the diagram converter locally
                    </a>{" "}
                    to convert this batch.
                  </p>
                </div>
              )}
              {files.map((file, idx) => (
                <FileItem
                  key={file.name + "-" + idx}
                  name={file.name}
                  status="edit"
                  onDelete={() => {
                    setFiles((prevFiles) =>
                      prevFiles.filter((prevFile) => prevFile !== file)
                    );
                  }}
                />
              ))}
            </div>
            <section className="versionSelector">
              <h2>Target version</h2>
              <p>
                Choose the Camunda 8 version to convert to. Defaults to the
                latest stable version (8.9).
              </p>
              <div
                ref={versionSegmentedRef}
                className="versionSegmented"
                role="radiogroup"
                aria-label="Target Camunda 8 version"
                onKeyDown={handleVersionKeyDown}
              >
                {SUPPORTED_PLATFORM_VERSIONS.map((version) => (
                  <button
                    key={version.value}
                    type="button"
                    role="radio"
                    aria-label={getPlatformVersionAriaLabel(version)}
                    aria-checked={platformVersion === version.value}
                    tabIndex={platformVersion === version.value ? 0 : -1}
                    className={
                      "versionSegment" +
                      (platformVersion === version.value
                        ? " versionSegment--selected"
                        : "")
                    }
                    onClick={() => setPlatformVersion(version.value)}
                  >
                    <span className="versionSegmentNumber">{version.label}</span>
                    <span className="versionSegmentHint">{version.hint}</span>
                  </button>
                ))}
              </div>
            </section>
            <p>
              Click the button below to analyze and convert your files.
            </p>

            <Form className="configBox" onSubmit={(e) => e.preventDefault()}>
              <h2>
                <Settings style={{ marginRight: '0.5rem' }} />
                Advanced options
                <Button
                  kind="ghost"
                  size="sm"
                  onClick={() => setShowConfig((prev) => !prev)}
                  className="withMarginBottom"
                >
                  {showConfig ? 'Hide' : 'Show'}
                </Button>
              </h2>
            {showConfig && (
                <FormGroup legendText="">
                  <Checkbox
                    id="appendDocumentationOnlyTaskAndWarning"
                    labelText="Append WARNING and TASK findings to BPMN documentation"
                    checked={configOptions.appendDocumentationOnlyTaskAndWarning}
                    aria-describedby="appendDocumentationOnlyTaskAndWarningHint"
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        appendDocumentationOnlyTaskAndWarning: checked,
                      }))
                    }
                  />
                  <p
                    id="appendDocumentationOnlyTaskAndWarningHint"
                    className="configOptionHint"
                  >
                    Appends findings with WARNING or TASK severity to the documentation of each
                    BPMN element, so you can act on them in the Modeler.
                    REVIEW and INFO messages are left out.
                  </p>
                  <div className="form-spacer" />
                  <Checkbox
                    id="addDataMigrationExecutionListener"
                    labelText="Add data migration execution listener"
                    checked={configOptions.addDataMigrationExecutionListener}
                    aria-describedby="addDataMigrationExecutionListenerHint"
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        addDataMigrationExecutionListener: checked,
                      }))
                    }
                  />
                  <p id="addDataMigrationExecutionListenerHint" className="configOptionHint">
                    Adds an execution listener to blank start events so the Camunda 7 Data
                    Migrator can track migrated instances.
                  </p>
                  <TextInput
                    id="dataMigrationExecutionListenerJobType"
                    labelText="Execution listener job type"
                    value={configOptions.dataMigrationExecutionListenerJobType}
                    disabled={!configOptions.addDataMigrationExecutionListener}
                    aria-describedby="dataMigrationExecutionListenerJobTypeHint"
                    onChange={(e) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        dataMigrationExecutionListenerJobType: e.target.value,
                      }))
                    }
                  />
                  <p id="dataMigrationExecutionListenerJobTypeHint" className="configOptionHint">
                    Job type used by the listener. Available when "Add data migration
                    execution listener" is selected.
                  </p>
                  <div className="form-spacer" />
                  <Checkbox
                    id="keepJobTypeBlank"
                    labelText="Keep job type blank"
                    checked={configOptions.keepJobTypeBlank}
                    aria-describedby="keepJobTypeBlankHint"
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        keepJobTypeBlank: checked,
                      }))
                    }
                  />
                  <p id="keepJobTypeBlankHint" className="configOptionHint">
                    Leaves the job type empty on converted delegates so you can set it
                    yourself after conversion.
                  </p>
                  <div className="form-spacer" />
                  <Checkbox
                    id="defaultJobTypeEnabled"
                    labelText="Always use default job type"
                    checked={configOptions.defaultJobTypeEnabled}
                    aria-describedby="alwaysUseDefaultJobTypeHint"
                    disabled={configOptions.keepJobTypeBlank}
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        defaultJobTypeEnabled: checked,
                      }))
                    }
                  />
                  <p id="alwaysUseDefaultJobTypeHint" className="configOptionHint">
                    Fills every delegate's job type with the default value below, for
                    example to route all delegates to one job worker such as the Camunda 7
                    Adapter. Available when "Keep job type blank" is cleared.
                  </p>
                  <TextInput
                    id="defaultJobType"
                    labelText="Default job type"
                    value={configOptions.defaultJobType}
                    disabled={configOptions.keepJobTypeBlank}
                    aria-describedby="defaultJobTypeHint"
                    onChange={(e) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        defaultJobType: e.target.value,
                      }))
                    }
                  />
                  <p id="defaultJobTypeHint" className="configOptionHint">
                    Job type applied when "Always use default job type" is selected.
                    Available when "Keep job type blank" is cleared.
                  </p>
                </FormGroup>
            )}
            </Form>




            <div className="analyzeButton">
              <Button
                kind="primary"
                size="lg"
                onClick={analyzeAndConvert}
                disabled={files.length === 0}
              >
                Analyze and convert
              </Button>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            {/*
            <section>
              <Callout
                kind="success"
                title="Analysis and convertion complete"
                lowContrast
              />
            </section>
            */}

            <div className="resultsNav">
              <Button kind="secondary" size="sm" onClick={backToConfigure}>
                Back to configure
              </Button>
              <Button kind="secondary" size="sm" onClick={startNewBatch}>
                Convert more files
              </Button>
            </div>
            <section>
              <h2 className="sectionHeading">Converted files</h2>
              <p>
                Download each converted file or all of them as a ZIP. Use the eye
                icon to preview analysis findings for each file; BPMN files also
                render a diagram, and forms show a form preview.
              </p>
              {files.map((file, idx) => {
                const result = fileResults[idx];
                const isForm = file.name.toLowerCase().endsWith(".form");
                return (
                <FileItem
                  key={file.name + "-" + idx}
                  name={file.name}
                  status={result.status}
                  isChecked={result.checkResponseJson != null}
                  isConverted={result.convertedFileBlob != null}
                  previewAction={isForm ? () => previewForm(result, file.name) : () => preview(result, file.name)}
                  previewTitle={isForm ? "Preview form" : undefined}
                  downloadAction={() => download(result)}
                  findingCount={buildFindingsRows(result.checkResponseJson).length}
                  highestSeverity={getHighestSeverity(buildFindingsRows(result.checkResponseJson))}
                  error={
                    result.status === "error"
                      ? result.errorMessage || "File processing failed"
                      : ""
                  }
                  onRetry={result.status === "error" ? () => retryFile(idx) : undefined}
                />
                );
              })}
              <Button
                kind="tertiary"
                size="lg"
                renderIcon={Download}
                onClick={downloadZIP}
                disabled={validFiles.length === 0}
              >
                Download all as ZIP
              </Button>
            </section>
            <hr />

            <section>
              <h2 className="sectionHeading">Analysis results</h2>
              <p>Download the findings for all converted files:</p>
              <div className="download-options">
                <div className="download-row">
                  <Button
                    kind="primary"
                    size="md"
                    renderIcon={Download}
                    onClick={downloadXLS}
                    disabled={validFiles.length === 0}
                  >
                    Download XLSX
                  </Button>
                  <p>
                    Excel workbook with all findings, ready to review and share.
                  </p>
                </div>
                <div className="download-row">
                  <Button
                    kind="primary"
                    size="md"
                    renderIcon={Download}
                    onClick={downloadCSV}
                    disabled={validFiles.length === 0}
                  >
                    Download CSV
                  </Button>
                  <p>
                    Plain-text findings to import into other tools.
                  </p>
                </div>
                <div className="download-row">
                  <Button
                    variant="secondary"
                    size="default"
                    onClick={downloadJSON}
                    disabled={validFiles.length === 0}
                  >
                    <Download />
                    Download JSON
                  </Button>
                  <p>
                    Machine-readable findings, for example as input for
                    migration tooling.
                  </p>
                </div>
                </div>
              <p>
                Learn more about the findings in the{" "}
                <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/#migration-analyzer" target="_blank" rel="noopener noreferrer">
                  documentation
                </a>
                .
              </p>
            </section>
            <hr />

            <section>
              <h2 className="sectionHeading">Next steps</h2>
              <p>
                Continue your Camunda 7 to 8 migration with the step-by-step
                migration guide.
              </p>
              <Button
                kind="tertiary"
                size="lg"
                renderIcon={Launch}
                href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-journey/?utm_source=analyzer"
                target="_blank"
                rel="noopener noreferrer"
              >
                Open migration guide
              </Button>
            </section>
          </>
        )}

      </div>
      </div>

{isPreviewOpen && (
  <div className="modal-backdrop">
    <div
      className="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="previewDialogTitle"
      tabIndex={-1}
      ref={previewDialogRef}
    >
      <div className="modal-header">
        <div className="left">
        <h2 id="previewDialogTitle">
          {previewFileName ? `Preview: ${previewFileName}` : "Preview"}
        </h2>
        </div>
        <div>
          <Button
            kind="secondary"
            size="sm"
            renderIcon={Close}
            onClick={() => setIsPreviewOpen(false)}
          >
            Close
          </Button>
        </div>
      </div>

      {(previewType === "bpmn" || previewType === "other") && (
        <>
          {previewType === "bpmn" && !previewDiagramError && (
            <div ref={bpmnPreviewRef} id="bpmnDiagram" className="diagram-container"></div>
          )}
          {(previewType === "other" || (previewType === "bpmn" && previewDiagramError)) && (
            <p style={{ marginTop: '1rem' }}>
              {previewDiagramError
                ? 'The diagram could not be rendered. The findings for this file are listed below.'
                : 'Diagram preview is only available for BPMN files. The findings for this file are listed below.'}
            </p>
          )}
          <FindingsSection
            header={previewTableHeader}
            rows={previewTableRows}
            onSelectElement={previewType === "bpmn" && !previewDiagramError ? selectFindingElement : undefined}
            selectedElementId={selectedFindingElementId}
          />
        </>
      )}
      {previewType === "form" && (
        <>
          {previewFormError
            ? <p className="form-preview-error" role="alert">{previewFormError}</p>
            : <FormPreview schema={previewFormSchema} onError={setPreviewFormError} />}
          <FindingsSection header={previewTableHeader} rows={previewTableRows} />
        </>
      )}


  </div>
  </div>
)}

    </div>


  );

}

export default App;