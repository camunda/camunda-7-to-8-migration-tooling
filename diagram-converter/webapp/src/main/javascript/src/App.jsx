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
import BpmnJS from 'bpmn-js';

// Target Camunda 8 versions offered in the UI. This is a curated subset of the
// versions the backend understands (SemanticVersion.java); we only surface the
// versions users realistically target today. The default mirrors the backend
// default in converter-properties.properties (zeebe-platform.version=8.9), which
// is the latest generally available release. 8.10 is offered for users already
// targeting the upcoming release.
const SUPPORTED_PLATFORM_VERSIONS = [
  { value: "8.8", label: "8.8", hint: "Previous stable" },
  { value: "8.9", label: "8.9", hint: "Latest stable" },
  { value: "8.10", label: "8.10", hint: "Next version" },
];
const DEFAULT_PLATFORM_VERSION = "8.9";

function App() {
  const baseUrl = ""; // Change this to "http://localhost:8080" if you want to play with it locally by using npm run dev

  const [step, setStep] = useState(0);
  const [files, setFiles] = useState([]);
  const [fileResults, setFileResults] = useState([]);
  const [validFiles, setValidFiles] = useState([]);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [previewbpmnXml, setPreviewbpmnXml] = useState("");
  const [previewCheckJson, setPreviewCheckJson] = useState([]);

  const [previewTableHeader, setPreviewTableHeader] = useState([]);
  const [previewTableRows, setPreviewTableRows] = useState([]);

  const [downloadError, setDownloadError] = useState(null);
  const [downloadErrorTitle, setDownloadErrorTitle] = useState("");

  const [platformVersion, setPlatformVersion] = useState(DEFAULT_PLATFORM_VERSION);

  const [showConfig, setShowConfig] = useState(false);
  const incompatibilityNotifRef = useRef(null);
  const versionSegmentedRef = useRef(null);

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
    ? fileResults.reduce((sum, r) => {
        if (!r.checkResponseJson) return sum;
        return sum + r.checkResponseJson
          .flatMap(item => item.results || [])
          .reduce((s, el) => s + (el.messages?.length || 0), 0);
      }, 0)
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
      if (isPreviewOpen && previewbpmnXml) {
          const viewer = new BpmnJS({ container: '#bpmnDiagram' });
          viewer.importXML(previewbpmnXml).then(() => {
            const canvas = viewer.get('canvas');
            canvas.zoom('fit-viewport');

            const elementsWithMessages =
              previewCheckJson?.[0]?.results?.filter((el) => el.messages?.length > 0) || [];

            elementsWithMessages.forEach((el) => {
              if (el.elementId) {
                  const severity = getMostSevere(el.messages);
                  if (severity) {
                    // Mark wit the same color everytime for the moment
                    //canvas.addMarker(el.elementId, `highlight-${severity.toLowerCase()}`);
                    canvas.addMarker(el.elementId, `highlight-info`);
                  }
              }
            });

          });

      }
    }, [isPreviewOpen, previewbpmnXml]);

  useEffect(() => {
    if (!allDone || totalFindings === 0) return;
    const timer = setTimeout(() => {
      const el = incompatibilityNotifRef.current?.querySelector('button');
      if (el && el === document.activeElement) el.blur();
    }, 0);
    return () => clearTimeout(timer);
  }, [allDone, totalFindings]);

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
            errorMessage: `Analysis failed (HTTP ${checkResponse.status})`,
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
            errorMessage: `Conversion failed (HTTP ${convertResponse.status})`,
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

  function buildErrorMessage(errorBody) {
    switch (errorBody.errorCode) {
      case "FILE_COUNT_LIMIT_EXCEEDED":
        return <>
          Too many files uploaded. The online version supports up to {errorBody.maxPartCount} parts per request.
          {" "}To learn how to run the diagram converter locally with a custom limit, consult the{" "}
          <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/#local-web-application"
            target="_blank" rel="noopener noreferrer">diagram converter guide</a>.
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
      "Downloading XLSX failed"
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
      "Downloading CSV failed"
    );
  }
  async function downloadZIP() {
    const formData = createFormData(validFiles);
    await handleDownloadResponse("converted-models.zip",
      await fetch(baseUrl + "/convertBatch", {
        body: formData,
        method: "POST",
      }),
      "Downloading ZIP failed"
    );
  }

  async function preview(response) {
    if (!response?.checkResponseJson) return;

    setPreviewTableHeader([
      { key: 'elementType', header: 'Element Type' },
      { key: 'elementId', header: 'Element ID' },
      { key: 'elementName', header: 'Element Name' },
      { key: 'severity', header: 'Severity' },
      { key: 'message', header: 'Message' },
      { key: 'link', header: 'Link' },
    ]);

    setPreviewTableRows(
      response.checkResponseJson?.[0]?.results.flatMap((element, elementIdx) =>
        element.messages.map((message, msgIdx) => ({
          id: `${elementIdx}-${msgIdx}`,
          elementType: element.elementType,
          elementId: element.elementId,
          elementName: element.elementName || '(unnamed)',
          severity: message.severity,
          message: message.message,
          link: message.link || null,
        }))
      ) || []);


    setPreviewCheckJson(response.checkResponseJson);
    setPreviewbpmnXml(response.originalModelXml);

    setIsPreviewOpen(true);
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
          Convert your BPMN and DMN models to Camunda 8 — then identify gaps and assess migration effort.
        </p>
        <div className="heroMeta">
          <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/"
            rel="noopener noreferrer" target="_blank">
            Diagram Converter Guide
          </a>
          <a href="https://github.com/camunda/camunda-7-to-8-migration-tooling/releases"
            rel="noopener noreferrer" target="_blank">
            Download local version
          </a>
          <a href="https://legal.camunda.com/licensing-and-other-legal-terms#trial-and-free"
            rel="noopener noreferrer" target="_blank">
            Legal terms &amp; data privacy
          </a>
        </div>
      </div>
      <div className="whiteBox centered">
        <div className="progressindicators">
          <Stepper currentStep={step === 0 ? 0 : 1} aria-label="Migration analysis and conversion steps">
            <StepperStep>Configure</StepperStep>
            <StepperStep>Results</StepperStep>
          </Stepper>
        </div>


        {step === 0 && (
          <>
            <section className="flowStep">
              <div className="flowStepHeader">
                <span className="flowStepNumber">1</span>
                <h4>Add your files</h4>
              </div>
              <p>Upload one or more BPMN and DMN models to analyze and convert.</p>
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
              <p>Choose your target Camunda 8 version. Defaults to the latest stable (8.9).</p>
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

              <form className="configBox" style={{ marginTop: "1.5rem" }}>
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
                      Advanced configuration options
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
                      <span>Add Data Migration Execution Listener</span>
                    </label>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="dataMigrationExecutionListenerJobType" className="text-sm font-medium">
                        Execution Listener Job Type
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
                      <span>Enable default job type</span>
                    </label>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="defaultJobType" className="text-sm font-medium">
                        Default Job Type
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
              <h3>Your Models</h3>
              <p>
                Download models converted to Camunda 8 individually or as one Zip
                file. You can also preview the analysis result on the BPMN model.
              </p>
              {allDone && totalFindings > 0 && (
                <div ref={incompatibilityNotifRef}>
                  <Alert
                    variant="warning"
                    title={`${totalFindings} finding${totalFindings !== 1 ? 's' : ''} detected for Camunda ${platformVersion}`}
                    description="Some elements may not be fully supported in this version. Use the preview per model or download the XLSX report for a complete overview."
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
                const fileFindingCount = r.checkResponseJson
                  ? r.checkResponseJson
                      .flatMap(item => item.results || [])
                      .reduce((s, el) => s + (el.messages?.length || 0), 0)
                  : 0;
                return (
                <FileItem
                  key={file.name + "-" + idx}
                  name={file.name}
                  status={r.status}
                  isChecked={r.checkResponseJson != null}
                  isConverted={r.convertedFileBlob != null}
                  previewAction={() => preview(r)}
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
                Download converted models as ZIP
              </Button>
            </section>
            <hr />

            <section>
              <h3>Analysis results</h3>
              <p>Download the  for all successfully converted models:</p>
              <div className="download-options">
                <div className="download-row">
                  <Button
                    variant="default"
                    size="default"
                    onClick={downloadXLS}
                    className="withMarginBottom"
                    disabled={validFiles.length === 0}
                  >
                    <Download />
                    Download XLSX
                  </Button>
                  <p>
                    Microsoft Excel file (XLSX) containing results and prepared
                    analysis.
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
                    Comma Separated Values (CSV) file containing plain results to
                    import into your favorite tooling.
                  </p>
                  </div>
                </div>
              <p>
                For more information on the analysis results,{" "}
                <a href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/#migration-analyzer" target="_blank">
                  see the documentation
                </a>
                .
              </p>
            </section>
            <hr />

            <h3>Next steps for your migration</h3>
            <section>
              <p>
                Discover next steps for your migration from Camunda 7 to Camunda
                8 in the migration guide.
              </p>
              <Button
                variant="secondary"
                size="lg"
                href="https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-journey/?utm_source=analyzer"
                target="_blank"
                rel="noopener noreferrer"
              >
                <ExternalLink />
                Migration Guide
              </Button>
            </section>
          </>
        )}

{isPreviewOpen && (
  <div className="modal-backdrop">
    <div className="modal">
      <div className="modal-header">
        <div className="left">
        <h2>Analysis preview</h2>
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

      <div id="bpmnDiagram" className="diagram-container"></div>
      {previewTableRows.length === 0 && (
        <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>No findings for this model.</p>
      )}
      {previewTableRows.length > 0 && <>
      <h3>Findings</h3>
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginBottom: '0.75rem' }}>
        Elements in this model that need attention during migration. Each row describes one finding — its location, severity, and a message explaining what to address.
      </p>
      <Table className="analysis-table">
        <TableHeader>
          <TableRow>
            {previewTableHeader.map((header) => (
              <TableHead key={header.key}>
                {header.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {previewTableRows.map((row) => (
            <TableRow key={row.id}>
              {previewTableHeader.map((header) => {
                const value = row[header.key];
                return (
                  <TableCell key={`${row.id}-${header.key}`}>
                    {header.key === 'link'
                      ? value
                        ? <a href={value} target="_blank" rel="noopener noreferrer">Link</a>
                        : '-'
                      : value}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      </>}

    </div>
  </div>
)}

      </div>
    </div>


  );

}

export default App;
