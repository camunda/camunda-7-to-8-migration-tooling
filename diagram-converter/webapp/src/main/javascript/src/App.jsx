/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState, useEffect, useRef } from "react";

import {
  Button,
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
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
import { FINDINGS_TABLE_HEADER, buildFindingsRows } from "./findings";
import BpmnJS from 'bpmn-js';
import DmnPreview from "./DmnPreview";
import FormPreview from "./FormPreview";
import { parseFormSchema } from "./formSchema";
import { getPreviewType } from "./modelType";

// Target Camunda 8 versions offered in the UI. This is a curated subset of the
// versions the backend understands (SemanticVersion.java); we only surface the
// versions users realistically target today. The default mirrors the backend
// default in converter-properties.properties (zeebe-platform.version=8.8), which
// is the latest generally available release for this release line. 8.9 is offered
// for users already targeting the upcoming release.
const SUPPORTED_PLATFORM_VERSIONS = [
  { value: "8.7", label: "8.7", hint: "Previous stable" },
  { value: "8.8", label: "8.8", hint: "Latest stable" },
  { value: "8.9", label: "8.9", hint: "Next version" },
];
const DEFAULT_PLATFORM_VERSION = "8.8";

function getMostSevere(messages) {
  const severityOrder = ['WARNING', 'TASK', 'REVIEW', 'INFO'];

  let mostSevere = 'INFO';

  for (const msg of messages) {
    if (
      severityOrder.indexOf(msg.severity) >
      severityOrder.indexOf(mostSevere)
    ) {
      mostSevere = msg.severity;
    }
  }

  return mostSevere;
}

function FindingsSection({ header, rows }) {
  if (rows.length === 0) {
    return (
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>No findings for this file.</p>
    );
  }
  return (
    <>
      <h3>Findings</h3>
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginBottom: '0.75rem' }}>
        Elements in this file that need attention during migration. Each row describes one finding — its location, severity, and a message explaining what to address.
      </p>
      <Table className="analysis-table">
        <TableHeader>
          <TableRow>
            {header.map((h) => (
              <TableHead key={h.key}>
                {h.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              {header.map((h) => {
                const value = row[h.key];
                return (
                  <TableCell key={`${row.id}-${h.key}`}>
                    {h.key === 'link'
                      ? value
                        ? <a
                            href={value}
                            target="_blank"
                            rel="noopener noreferrer"
                            aria-label={`Open finding documentation: ${value}`}
                          >
                            Open
                          </a>
                        : '-'
                      : value}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}

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

  const [previewTableHeader, setPreviewTableHeader] = useState([]);
  const [previewTableRows, setPreviewTableRows] = useState([]);

  const [downloadError, setDownloadError] = useState(null);
  const [downloadErrorTitle, setDownloadErrorTitle] = useState("");

  const [platformVersion, setPlatformVersion] = useState(DEFAULT_PLATFORM_VERSION);

  const [showConfig, setShowConfig] = useState(false);
  const incompatibilityNotifRef = useRef(null);
  const versionSegmentedRef = useRef(null);
  const bpmnPreviewRef = useRef(null);

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
  });


  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setIsPreviewOpen(false); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

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
            const severity = getMostSevere(el.messages);
            if (severity) {
              // Mark with the same color every time for the moment
              //canvas.addMarker(el.elementId, `highlight-${severity.toLowerCase()}`);
              canvas.addMarker(el.elementId, `highlight-info`);
            }
          }
        });

      }).catch((error) => {
        if (isActive) {
          console.error("Unable to render BPMN preview:", error);
          setPreviewDiagramError(true);
        }
      });

      return () => {
        isActive = false;
        viewer.destroy();
      };
    }, [
      isPreviewOpen,
      previewType,
      previewDiagramError,
      previewModelXml,
      previewCheckJson,
    ]);

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
    return formData;
  }

  function updateFileResult(idx, result) {
    setFileResults((prevResults) => {
      const updated = [...prevResults];
      updated[idx] = result;
      return updated;
    });
  }

  async function analyzeAndConvert() {
    setStep(2);
    setFileResults(files.map(() => ({ status: "uploading" })));

    const uploadResults = await Promise.all(
      files.map(async (file, idx) => {
        const formData = createFormData(file);
        const originalModelXml = await file.text();
        const checkResponse = await fetch(baseUrl + "/check", {
          body: formData,
          method: "POST",
          headers: {
             "Accept": "application/json"
          },
        });

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

        const checkResponseJson = await checkResponse.json();

        let result = {
          status: "uploading",
          originalModelXml: originalModelXml,
          checkResponseJson: checkResponseJson,
        };
        updateFileResult(idx, result);

        const convertResponse = await fetch(baseUrl + "/convert", {
          body: formData,
          method: "POST",
        });

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

        // Convert response to blob
        const blob = await convertResponse.blob();

        result = {
          status: "success",
          originalModelXml: originalModelXml,
          checkResponseJson: checkResponseJson,
          convertedFileBlob: blob,
          filename
        };

        updateFileResult(idx, result);
        return result;
      })
    );

    const validFiles = files.filter(
      (_, idx) => uploadResults[idx].status === "success"
    );
    setValidFiles(validFiles);
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
          <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/#local-web-application"
            target="_blank" rel="noopener noreferrer">run the diagram converter locally</a>.
        </>;
      default:
        return "Download failed. Please try again.";
    }
  }

  async function handleDownloadResponse(filename, response, title) {
    if (!response.ok) {
      let errorMessage = "Download failed. Please try again.";
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

  async function preview(response, modelType) {
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
    // BPMN is detected by content, not extension: the dropzone also accepts
    // .xml files, which can be BPMN (or DMN) models.
    setPreviewType(
      modelType === "dmn"
        ? "dmn"
        : typeof response.originalModelXml === "string" &&
            response.originalModelXml.includes("omg.org/spec/BPMN")
          ? "bpmn"
          : "other"
    );

    setIsPreviewOpen(true);
  }

  function openFormPreview(schema, errorMessage = "") {
    setPreviewFormSchema(schema);
    setPreviewFormError(errorMessage);
    setPreviewModelXml("");
    setPreviewCheckJson([]);
    setPreviewTableHeader([]);
    setPreviewTableRows([]);
    setPreviewDmnError("");
    setPreviewDiagramError(false);
    setPreviewType("form");
    setIsPreviewOpen(true);
  }

  async function previewForm(response) {
    const formContent = response?.convertedFileBlob
      ? await response.convertedFileBlob.text()
      : response?.originalModelXml;
    const { schema, error } = parseFormSchema(formContent);
    openFormPreview(schema, error);
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
      <div className="whiteBox hero">
        <h2>Camunda Migration Analyzer &amp; Diagram Converter</h2>
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
                <span className="flowStepNumber">1</span>
                <h4>Add files</h4>
              </div>
              <p>Upload BPMN or DMN models to analyze and convert, or Camunda Forms to convert.</p>
              <div className="fileUploadBox">
                <DropZone
                  onFiles={(files) => {
                    setFiles((prevFiles) => [...prevFiles, ...files]);
                  }}
                />
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
                <span className="flowStepNumber">2</span>
                <h4>Configure conversion</h4>
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
                        id="addDataMigrationExecutionListener"
                        checked={configOptions.addDataMigrationExecutionListener}
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            addDataMigrationExecutionListener: checked === true,
                          }))
                        }
                      />
                      <span>Add data migration execution listener</span>
                    </label>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="dataMigrationExecutionListenerJobType" className="text-sm font-medium">
                        Execution listener job type
                      </label>
                      <Input
                        id="dataMigrationExecutionListenerJobType"
                        value={configOptions.dataMigrationExecutionListenerJobType}
                        disabled={!configOptions.addDataMigrationExecutionListener}
                        onChange={(e) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            dataMigrationExecutionListenerJobType: e.target.value,
                          }))
                        }
                      />
                    </div>
                    <div className="form-spacer" />
                    <label className="flex items-center gap-2">
                      <Checkbox
                        id="keepJobTypeBlank"
                        checked={configOptions.keepJobTypeBlank}
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            keepJobTypeBlank: checked === true,
                          }))
                        }
                      />
                      <span>Keep job type blank</span>
                    </label>
                    <div className="form-spacer" />
                    <label className="flex items-center gap-2">
                      <Checkbox
                        id="alwaysUseDefaultJobType"
                        checked={configOptions.alwaysUseDefaultJobType}
                        disabled={configOptions.keepJobTypeBlank}
                        onCheckedChange={(checked) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            alwaysUseDefaultJobType: checked === true,
                          }))
                        }
                      />
                      <span>Always use default job type</span>
                    </label>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="defaultJobType" className="text-sm font-medium">
                        Default job type
                      </label>
                      <Input
                        id="defaultJobType"
                        value={configOptions.defaultJobType}
                        disabled={configOptions.keepJobTypeBlank}
                        onChange={(e) =>
                          setConfigOptions((prev) => ({
                            ...prev,
                            defaultJobType: e.target.value,
                          }))
                        }
                      />
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
            <section>
              <h3>Converted files</h3>
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
                const fileFindingCount = buildFindingsRows(r.checkResponseJson).length;
                return (
                <FileItem
                  key={file.name + "-" + idx}
                  name={file.name}
                  status={r.status}
                  isChecked={r.checkResponseJson != null}
                  isConverted={r.convertedFileBlob != null}
                  previewAction={isForm ? () => previewForm(r) : () => preview(r, modelType)}
                  previewTitle={isForm ? "Preview form" : undefined}
                  downloadAction={() => download(r)}
                  findingCount={fileFindingCount}
                  error={
                    r.status === "error"
                      ? (r.errorMessage || "File processing failed")
                      : ""
                  }
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
              <h3>Analysis results</h3>
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
                    Machine-readable findings, e.g. as input for AI-assisted
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

            <h3>Next steps</h3>
            <section>
              <p>
                Continue your Camunda 7 to 8 migration with the step-by-step
                migration guide.
              </p>
              <Button
                variant="secondary"
                size="lg"
                href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-journey/?utm_source=analyzer"
                target="_blank"
                rel="noopener noreferrer"
              >
                <ExternalLink />
                Open migration guide
              </Button>
            </section>
          </>
        )}

{isPreviewOpen && (
  <div className="modal-backdrop">
    <div className="modal">
      <div className="modal-header">
        <div className="left">
        <h2>Preview</h2>
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
          <FindingsSection header={previewTableHeader} rows={previewTableRows} />
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
    </div>


  );

}

export default App;
