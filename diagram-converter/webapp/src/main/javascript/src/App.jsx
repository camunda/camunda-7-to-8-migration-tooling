/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState, useEffect, useLayoutEffect, useRef } from "react";

import {
  Button,
  Checkbox,
  Input,
  Alert,
  Stepper,
  StepperStep,
} from "@camunda/design-system";

// Carbon icons → lucide-react equivalents:
//   Launch → ExternalLink, Close → X (rest keep their names)
import { Download, ExternalLink, X, Settings, ChevronDown, ChevronUp } from "lucide-react";
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
import DmnPreview from "./DmnPreview";
import FormPreview from "./FormPreview";
import { parseFormSchema } from "./formSchema";
import { getPreviewType } from "./modelType";
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
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [previewType, setPreviewType] = useState(null);
  const [previewModelXml, setPreviewModelXml] = useState("");
  const [previewFormSchema, setPreviewFormSchema] = useState(null);
  const [previewFormError, setPreviewFormError] = useState("");
  const [previewDmnError, setPreviewDmnError] = useState("");
  const [previewDiagramError, setPreviewDiagramError] = useState(false);
  const [previewCheckJson, setPreviewCheckJson] = useState([]);
  const [previewFileName, setPreviewFileName] = useState("");
  const [selectedFindingElementId, setSelectedFindingElementId] = useState(null);

  const [previewTableHeader, setPreviewTableHeader] = useState([]);
  const [previewTableRows, setPreviewTableRows] = useState([]);

  const [downloadError, setDownloadError] = useState(null);
  const [downloadErrorTitle, setDownloadErrorTitle] = useState("");

  const [platformVersion, setPlatformVersion] = useState(DEFAULT_PLATFORM_VERSION);

  const [showConfig, setShowConfig] = useState(false);
  const incompatibilityNotifRef = useRef(null);
  const versionSegmentedRef = useRef(null);
  const bpmnPreviewRef = useRef(null);
  const bpmnViewerRef = useRef(null);
  const selectedMarkerElementIdRef = useRef(null);
  const previewDialogRef = useRef(null);

  function handleVersionKeyDown(e) {
    const keys = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'];
    if (!keys.includes(e.key)) return;
    e.preventDefault();
    const currentIdx = SUPPORTED_PLATFORM_VERSIONS.findIndex(v => v.value === platformVersion);
    let nextIdx = currentIdx;
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      nextIdx = (currentIdx + 1) % SUPPORTED_PLATFORM_VERSIONS.length;
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      nextIdx = (currentIdx - 1 + SUPPORTED_PLATFORM_VERSIONS.length) % SUPPORTED_PLATFORM_VERSIONS.length;
    } else if (e.key === 'Home') {
      nextIdx = 0;
    } else if (e.key === 'End') {
      nextIdx = SUPPORTED_PLATFORM_VERSIONS.length - 1;
    }
    setPlatformVersion(SUPPORTED_PLATFORM_VERSIONS[nextIdx].value);
    versionSegmentedRef.current?.querySelectorAll('button')[nextIdx]?.focus();
  }

  const allDone = fileResults.length > 0 && fileResults.every(r => r.status !== 'uploading');
  const totalFindings = allDone
    ? fileResults.reduce((sum, r) => sum + buildFindingsRows(r.checkResponseJson).length, 0)
    : 0;

  const [configOptions, setConfigOptions] = useState({
    defaultJobType: "camunda-7-job",
    keepJobTypeBlank: false,
    alwaysUseDefaultJobType: false,
    addDataMigrationExecutionListener: false,
    dataMigrationExecutionListenerJobType: "migrator",
    appendDocumentationOnlyTaskAndWarning: false,
  });


  useEffect(() => {
    if (
      !isPreviewOpen ||
      previewType !== "bpmn" ||
      previewDiagramError ||
      !previewModelXml
    ) {
      return;
    }

    const viewer = new BpmnJS({ container: bpmnPreviewRef.current });
    let isActive = true;
    viewer.importXML(previewModelXml).then(() => {
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
    }, [
      isPreviewOpen,
      previewType,
      previewDiagramError,
      previewModelXml,
      previewCheckJson,
    ]);

  // Locates a finding's element in the rendered BPMN diagram: scrolls it
  // into view, applies bpmn-js's own selection outline, and layers an
  // extra `finding-selected` marker so the row ↔ element relationship is
  // visible even once the built-in selection outline fades. No-ops when
  // the diagram isn't ready or the element can't be found (e.g. a stale
  // reference), preserving the existing graceful fallback behavior.
  function selectFindingElement(elementId) {
    if (!elementId || elementId === '-') return;
    const viewer = bpmnViewerRef.current;
    if (!viewer) return;

    try {
      const elementRegistry = viewer.get('elementRegistry');
      const element = elementRegistry.get(elementId);
      if (!element) return;

      const canvas = viewer.get('canvas');
      if (selectedMarkerElementIdRef.current && selectedMarkerElementIdRef.current !== elementId) {
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

  // Turns the preview overlay into a real modal dialog while it is open:
  // moves focus in, traps Tab/Shift+Tab within it, closes on Escape, locks
  // background scrolling, and restores focus to whatever opened it on close.
  // Uses useLayoutEffect (not useEffect) so the initial focus move happens
  // synchronously right after the dialog mounts, before paint — avoiding a
  // race where focus briefly stays outside the dialog on slower runners.
  useLayoutEffect(() => {
    if (!isPreviewOpen) return undefined;

    const dialogEl = previewDialogRef.current;
    const opener = document.activeElement;
    const previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const focusableSelector =
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    function getFocusable() {
      return dialogEl ? Array.from(dialogEl.querySelectorAll(focusableSelector)) : [];
    }

    const initialFocusTarget = getFocusable()[0] || dialogEl;
    initialFocusTarget?.focus();

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
      const isInsideDialog = dialogEl.contains(active);

      if (e.shiftKey && (active === first || !isInsideDialog)) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && (active === last || !isInsideDialog)) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown, true);

    return () => {
      document.removeEventListener('keydown', handleKeyDown, true);
      document.body.style.overflow = previousBodyOverflow;
      if (opener instanceof HTMLElement && document.contains(opener)) {
        opener.focus();
      }
    };
  }, [isPreviewOpen]);

  useEffect(() => {
    if (!allDone || totalFindings === 0) return;
    const timer = setTimeout(() => {
      const el = incompatibilityNotifRef.current?.querySelector('button');
      if (el && el === document.activeElement) el.blur();
    }, 0);
    return () => clearTimeout(timer);
  }, [allDone, totalFindings]);

  function createFormData(files) {
    const formData = new FormData();

    // Normalize to an array (you can pass a single file or an array of files)
    const fileArray = Array.isArray(files) ? files : [files];

    fileArray.forEach((file) => {
      // Append each file, optionally using indexed keys if needed
      formData.append("file", file);
    });

    // Target Camunda 8 platform version chosen by the user. Sent on /check,
    // /convert and /convertBatch so the backend converts for the right target.
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
    setDownloadError(null);
    setDownloadErrorTitle("");
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

  function buildErrorMessage(errorBody) {
    switch (errorBody.errorCode) {
      case "FILE_COUNT_LIMIT_EXCEEDED":
        return <>
          Too many files at once. Remove some files and try again.
          {" "}To convert larger sets,{" "}
          <a href={LOCAL_CONVERTER_DOCS_URL}
            target="_blank" rel="noopener noreferrer">run the diagram converter locally</a>.
        </>;
      default:
        return "Download failed. Try again.";
    }
  }

  async function handleDownloadResponse(filename, response, title) {
    if (!response.ok) {
      let errorMessage = "Download failed. Try again.";
      try {
        const errorBody = await response.json();
        errorMessage = buildErrorMessage(errorBody);
      } catch {
        // Response body is not JSON, use default message
      }
      setDownloadErrorTitle(title);
      setDownloadError(errorMessage);
      return;
    }
    setDownloadError(null);
    await download1(filename, response);
  }

  async function downloadXLS() {
    const formData = createFormData(validFiles);
    await handleDownloadResponse("analysis.xlsx",
      await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
          Accept:
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        },
      }),
      "XLSX download failed"
    );
  }
  async function downloadCSV() {
    const formData = createFormData(validFiles);
    await handleDownloadResponse("analysis.csv",
      await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
          Accept: "text/csv",
        },
      }),
      "CSV download failed"
    );
  }
  async function downloadJSON() {
    const formData = createFormData(validFiles);
    await handleDownloadResponse("analysis-results.json",
      await fetch(baseUrl + "/check", {
        body: formData,
        method: "POST",
        headers: {
          Accept: "application/vnd.camunda.analysis+json",
        },
      }),
      "JSON download failed"
    );
  }
  async function downloadZIP() {
    const formData = createFormData(validFiles);
    await handleDownloadResponse("converted-models.zip",
      await fetch(baseUrl + "/convertBatch", {
        body: formData,
        method: "POST",
      }),
      "ZIP download failed"
    );
  }

  async function preview(response, modelType, fileName) {
    if (!response?.checkResponseJson) return;

    setPreviewTableHeader(FINDINGS_TABLE_HEADER);
    setPreviewTableRows(buildFindingsRows(response.checkResponseJson));

    setPreviewCheckJson(response.checkResponseJson);
    const modelXml =
      (modelType === "bpmn" || modelType === "dmn") &&
      response?.convertedFileBlob
        ? await response.convertedFileBlob.text()
        : response.originalModelXml;
    setPreviewModelXml(modelXml);
    setPreviewFormSchema(null);
    setPreviewFormError("");
    setPreviewDmnError("");
    setPreviewDiagramError(false);
    setPreviewType(modelType);
    setPreviewFileName(fileName || "");
    setSelectedFindingElementId(null);

    setIsPreviewOpen(true);
  }

  function openFormPreview(schema, errorMessage = "", fileName = "") {
    setPreviewFormSchema(schema);
    setPreviewFormError(errorMessage);
    setPreviewModelXml("");
    setPreviewCheckJson([]);
    setPreviewTableHeader([]);
    setPreviewTableRows([]);
    setPreviewDmnError("");
    setPreviewDiagramError(false);
    setPreviewType("form");
    setPreviewFileName(fileName);
    setSelectedFindingElementId(null);
    setIsPreviewOpen(true);
  }

  async function previewForm(response, fileName) {
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
      <div className="whiteBox hero">
        <h1>Camunda Migration Analyzer &amp; Diagram Converter</h1>
        <p>
          Convert BPMN, DMN, and Camunda Form files to Camunda 8 and see what needs attention.
        </p>
        <div className="heroMeta">
          <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/"
            rel="noopener noreferrer" target="_blank">
            Documentation
          </a>
          <a href="https://github.com/camunda/camunda-7-to-8-migration-tooling/releases"
            rel="noopener noreferrer" target="_blank">
            Download local version
          </a>
          <a href="https://legal.camunda.com/licensing-and-other-legal-terms#trial-and-free"
            rel="noopener noreferrer" target="_blank">
            Legal &amp; privacy
          </a>
        </div>
      </div>
      <div className="whiteBox centered">
        <div className="progressindicators">
          <Stepper currentStep={step === 0 ? 0 : 1} aria-label="Conversion steps">
            <StepperStep>Configure</StepperStep>
            <StepperStep>Results</StepperStep>
          </Stepper>
        </div>


        {step === 0 && (
          <>
            <section className="flowStep">
              <div className="flowStepHeader">
                <span className="flowStepNumber">A</span>
                <h2>Add files</h2>
              </div>
              <p>Upload BPMN, DMN, or Camunda Form files to analyze and convert.</p>
              <p className="uploadGuidance">
                Batch actions (ZIP download, XLSX/CSV/JSON reports) support up to {MAX_BATCH_FILES} files.
                Files are processed by Camunda&apos;s hosted service. To convert more files or keep
                sensitive models private,{" "}
                <a href={LOCAL_CONVERTER_DOCS_URL} target="_blank" rel="noopener noreferrer">
                  run the converter locally
                </a>.
              </p>
              <div className="fileUploadBox">
                <DropZone
                  onFiles={(files) => {
                    setFiles((prevFiles) => [...prevFiles, ...files]);
                  }}
                />
                {files.length >= BATCH_FILE_WARNING_THRESHOLD && (
                  <Alert
                    variant="warning"
                    title={
                      files.length > MAX_BATCH_FILES
                        ? `Batch limit exceeded (${MAX_BATCH_FILES} max, ${files.length} added)`
                        : files.length === MAX_BATCH_FILES
                        ? `Batch limit reached (${MAX_BATCH_FILES} files)`
                        : `Approaching the batch limit (${files.length} of ${MAX_BATCH_FILES} files)`
                    }
                    description={
                      <>
                        Combined ZIP and analysis-report downloads support up to{" "}
                        {MAX_BATCH_FILES} files. Remove some files, or{" "}
                        <a href={LOCAL_CONVERTER_DOCS_URL} target="_blank" rel="noopener noreferrer">
                          run the diagram converter locally
                        </a>{" "}
                        to convert this batch.
                      </>
                    }
                    className="uploadLimitNotice"
                  />
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
            </section>

            <section className="flowStep">
              <div className="flowStepHeader">
                <span className="flowStepNumber">B</span>
                <h2>Configure conversion</h2>
              </div>
              <p>Choose the Camunda 8 version to convert to.</p>
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
                      (platformVersion === version.value ? " versionSegment--selected" : "")
                    }
                    onClick={() => setPlatformVersion(version.value)}
                  >
                    <span className="versionSegmentNumber">{version.label}</span>
                    {version.hint && (
                      <span className="versionSegmentHint">{version.hint}</span>
                    )}
                  </button>
                ))}
              </div>

              <form className="configBox" style={{ marginTop: "1.5rem" }} onSubmit={(e) => e.preventDefault()}>
                <button
                  type="button"
                  className="configToggle"
                  aria-expanded={showConfig}
                  onClick={() => setShowConfig((prev) => !prev)}
                >
                  <span className="configToggleLabel">
                    <Settings />
                    Advanced options
                  </span>
                  {showConfig ? <ChevronUp /> : <ChevronDown />}
                </button>
              {showConfig && (
                  <fieldset className="flex flex-col gap-2 rounded-md border bg-background p-4">
                    <legend className="px-1 text-sm font-medium text-foreground">
                      Advanced options
                    </legend>
                    <label className="flex items-center gap-2">
                      <Checkbox
                        id="appendDocumentationOnlyTaskAndWarning"
                        checked={configOptions.appendDocumentationOnlyTaskAndWarning}
                        aria-describedby="appendDocumentationOnlyTaskAndWarningHint"
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            appendDocumentationOnlyTaskAndWarning: checked === true,
                          }))
                        }
                      />
                      <span>Append only WARNING and TASK findings to BPMN documentation</span>
                    </label>
                    <p
                      id="appendDocumentationOnlyTaskAndWarningHint"
                      className="configOptionHint"
                    >
                      Writes "No direct mapping" (WARNING) and "Manual action required" (TASK)
                      findings into the documentation of each BPMN element, so you can act on
                      them in the Modeler. "Verify after conversion" (REVIEW) and "No action
                      needed" (INFO) findings are left out, and DMN and form files are not
                      affected.
                    </p>
                    <div className="form-spacer" />
                    <label className="flex items-center gap-2">
                      <Checkbox
                        id="addDataMigrationExecutionListener"
                        checked={configOptions.addDataMigrationExecutionListener}
                        aria-describedby="addDataMigrationExecutionListenerHint"
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            addDataMigrationExecutionListener: checked === true,
                          }))
                        }
                      />
                      <span>Add data migration execution listener</span>
                    </label>
                    <p id="addDataMigrationExecutionListenerHint" className="configOptionHint">
                      Adds an execution listener to blank start events so the Camunda 7 Data
                      Migrator can track migrated instances.
                    </p>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="dataMigrationExecutionListenerJobType" className="text-sm font-medium">
                        Execution listener job type
                      </label>
                      <Input
                        id="dataMigrationExecutionListenerJobType"
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
                    </div>
                    <div className="form-spacer" />
                    <label className="flex items-center gap-2">
                      <Checkbox
                        id="keepJobTypeBlank"
                        checked={configOptions.keepJobTypeBlank}
                        aria-describedby="keepJobTypeBlankHint"
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            keepJobTypeBlank: checked === true,
                          }))
                        }
                      />
                      <span>Keep job type blank</span>
                    </label>
                    <p id="keepJobTypeBlankHint" className="configOptionHint">
                      Leaves the job type empty on converted delegates so you can set it
                      yourself after conversion.
                    </p>
                    <div className="form-spacer" />
                    <label className="flex items-center gap-2">
                      <Checkbox
                        id="alwaysUseDefaultJobType"
                        checked={configOptions.alwaysUseDefaultJobType}
                        disabled={configOptions.keepJobTypeBlank}
                        aria-describedby="alwaysUseDefaultJobTypeHint"
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            alwaysUseDefaultJobType: checked === true,
                          }))
                        }
                      />
                      <span>Always use default job type</span>
                    </label>
                    <p id="alwaysUseDefaultJobTypeHint" className="configOptionHint">
                      Fills every delegate's job type with the default value below, for
                      example to route all delegates to one job worker such as the Camunda 7
                      Adapter. Available when "Keep job type blank" is cleared.
                    </p>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="defaultJobType" className="text-sm font-medium">
                        Default job type
                      </label>
                      <Input
                        id="defaultJobType"
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
                    </div>
                  </fieldset>
              )}
              </form>
            </section>

            <div className="convertAction">
              <Button
                variant="default"
                size="lg"
                onClick={analyzeAndConvert}
                disabled={files.length === 0}
              >
                Analyze and convert to Camunda<span className="ctaVersion">&nbsp;{platformVersion}</span>
              </Button>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <div className="resultsNav">
              <Button variant="secondary" size="sm" onClick={backToConfigure}>
                Back to configure
              </Button>
              <Button variant="secondary" size="sm" onClick={startNewBatch}>
                Convert more files
              </Button>
            </div>
            <section>
              <h2 className="sectionHeading">Converted files</h2>
              <p>
                Download each converted file or all of them as a ZIP. Use the eye
                icon to preview analysis findings for each file; BPMN files render
                a diagram, DMN files render a decision diagram, and forms show a
                form preview.
              </p>
              {allDone && totalFindings > 0 && (
                <div ref={incompatibilityNotifRef}>
                  <Alert
                    variant="warning"
                    title={`${totalFindings} finding${totalFindings !== 1 ? 's' : ''} detected for Camunda ${platformVersion}`}
                    description="Some elements may not be fully supported in this version. Use the preview per file or download the XLSX report for a complete overview."
                    className="incompatibility-notification"
                  >
                    <Button variant="secondary" size="sm" onClick={downloadXLS}>
                      Download XLSX
                    </Button>
                  </Alert>
                </div>
              )}
              {files.map((file, idx) => {
                const r = fileResults[idx];
                const modelType = getPreviewType(file.name);
                const isForm = modelType === "form";
                const fileFindingRows = buildFindingsRows(r.checkResponseJson);
                const fileFindingCount = fileFindingRows.length;
                const fileHighestSeverity = getHighestSeverity(fileFindingRows);
                return (
                <FileItem
                  key={file.name + "-" + idx}
                  name={file.name}
                  status={r.status}
                  isChecked={r.checkResponseJson != null}
                  isConverted={r.convertedFileBlob != null}
                  previewAction={isForm ? () => previewForm(r, file.name) : () => preview(r, modelType, file.name)}
                  previewTitle={isForm ? "Preview form" : undefined}
                  downloadAction={() => download(r)}
                  findingCount={fileFindingCount}
                  highestSeverity={fileHighestSeverity}
                  error={
                    r.status === "error"
                      ? (r.errorMessage || "File processing failed")
                      : ""
                  }
                  onRetry={r.status === "error" ? () => retryFile(idx) : undefined}
                />
                );
              })}
              {downloadError && (
                <Alert
                  variant="destructive"
                  title={downloadErrorTitle}
                  description={downloadError}
                  dismissible
                  onDismiss={() => setDownloadError(null)}
                  className="download-error-notification"
                />
              )}
              <Button
                variant="default"
                size="lg"
                onClick={downloadZIP}
                disabled={validFiles.length === 0}
              >
                <Download />
                Download all converted files as ZIP
              </Button>
            </section>
            <hr />

            <section>
              <h2 className="sectionHeading">Analysis results</h2>
              <p>Download the analysis results for all successfully converted files:</p>
              <div className="download-options">
                <div className="download-row">
                  <Button
                    variant="default"
                    size="default"
                    onClick={downloadXLS}
                    disabled={validFiles.length === 0}
                  >
                    <Download />
                    Download XLSX
                  </Button>
                  <p>
                    Excel workbook with all findings, ready to review and share.
                  </p>
                </div>
                <div className="download-row">
                  <Button
                    variant="secondary"
                    size="default"
                    onClick={downloadCSV}
                    disabled={validFiles.length === 0}
                  >
                    <Download />
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
                    AI-assisted migration tooling.
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
              <Button variant="secondary" size="lg" asChild>
                <a
                  href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-journey/?utm_source=analyzer"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <ExternalLink />
                  Open migration guide
                </a>
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
            variant="ghost"
            size="sm"
            onClick={() => setIsPreviewOpen(false)}
          >
            <X />
            Close
          </Button>
        </div>
      </div>

      {(previewType === "bpmn" || previewType === "dmn" || previewType === "other") && (
        <>
          {previewType === "bpmn" && !previewDiagramError && (
            <div ref={bpmnPreviewRef} id="bpmnDiagram" className="diagram-container"></div>
          )}
          {previewType === "dmn" &&
            (previewDmnError ? (
              <Alert
                variant="destructive"
                title="DMN preview unavailable"
                description={previewDmnError}
              />
            ) : (
              <DmnPreview xml={previewModelXml} onError={setPreviewDmnError} />
            ))}
          {(previewType === "other" || (previewType === "bpmn" && previewDiagramError)) && (
            <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>
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
            ? <Alert variant="destructive" title="Form preview unavailable" description={previewFormError} />
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
