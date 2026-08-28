/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState, useEffect } from "react";

import {
  ProgressIndicator,
  ProgressStep,
  Button,
  Callout,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  Form,
  FormGroup,
  Checkbox,
  TextInput,
} from "@carbon/react";

import { Download, Launch, Close, Settings } from "@carbon/react/icons";
import DropZone from "./DropZone";
import FileItem from "./FileItem";
import { FINDINGS_TABLE_HEADER, buildFindingsRows } from "./findings";
import BpmnJS from 'bpmn-js';
import FormPreview from "./FormPreview";
import { parseFormSchema } from "./formSchema";

function FindingsSection({ header, rows }) {
  if (rows.length === 0) {
    return <p style={{ marginTop: '1rem' }}>No findings for this file.</p>;
  }
  return (
    <>
      <h3>Findings</h3>
      <p style={{ marginBottom: '0.75rem' }}>
        Elements in this file that need attention during migration. Each row describes one finding - its location, severity, and a message explaining what to address.
      </p>
      <Table className="analysis-table">
        <TableHead>
          <TableRow>
            {header.map((h) => (
              <TableHeader key={h.key}>
                {h.header}
              </TableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              {header.map((h) => (
                <TableCell key={`${row.id}-${h.key}`}>
                  {h.key === 'link'
                    ? row.link
                      ? <a href={row.link} target="_blank" rel="noopener noreferrer">Link</a>
                      : '-'
                    : row[h.key]}
                </TableCell>
              ))}
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
  const isSaaS = window.location.hostname !== "localhost";

  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [previewType, setPreviewType] = useState(null);
  const [previewbpmnXml, setPreviewbpmnXml] = useState("");
  const [previewFormSchema, setPreviewFormSchema] = useState(null);
  const [previewFormError, setPreviewFormError] = useState("");
  const [previewDiagramError, setPreviewDiagramError] = useState(false);
  const [previewCheckJson, setPreviewCheckJson] = useState([]);

  const [previewTableHeader, setPreviewTableHeader] = useState([]);
  const [previewTableRows, setPreviewTableRows] = useState([]);

  const [showConfig, setShowConfig] = useState(false);
  const [configOptions, setConfigOptions] = useState({
    defaultJobType: "camunda-7-job",
    keepJobTypeBlank: false,
    alwaysUseDefaultJobType: false,
    addDataMigrationExecutionListener: false,
    dataMigrationExecutionListenerJobType: "=if legacyId != null then \"migrator\" else \"noop\"",
  });

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

  useEffect(() => {
      if (!isPreviewOpen || previewType !== "bpmn" || previewDiagramError || !previewbpmnXml) return;

      const viewer = new BpmnJS({ container: '#bpmnDiagram' });
      viewer.importXML(previewbpmnXml).then(() => {
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
        console.error("Unable to render BPMN preview:", error);
        setPreviewDiagramError(true);
      });

      return () => viewer.destroy();
    }, [isPreviewOpen, previewType, previewDiagramError, previewbpmnXml, previewCheckJson]);

  function createFormData(files) {
    const formData = new FormData();

    // Normalize to an array (you can pass a single file or an array of files)
    const fileArray = Array.isArray(files) ? files : [files];

    fileArray.forEach((file) => {
      // Append each file, optionally using indexed keys if needed
      formData.append("file", file);
    });

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
          setFileResults((prevResults) => {
            const updated = [...prevResults];
            updated[idx] = result;
            return updated;
          });
          return result;
        }

        const checkResponseJson = await checkResponse.json();

        let result = {
          status: "uploading",
          originalModelXml: originalModelXml,
          checkResponseJson: checkResponseJson,
        };
        setFileResults((prevResults) => {
          const updated = [...prevResults];
          updated[idx] = result;
          return updated;
        });

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
          setFileResults((prevResults) => {
            const updated = [...prevResults];
            updated[idx] = result;
            return updated;
          });
          return result;
        }

        // Convert response to blob
        const blob = await convertResponse.blob();

        result = {
          status: checkResponse.ok && convertResponse.ok ? "success" : "error",
          originalModelXml: originalModelXml,
          checkResponseJson: checkResponseJson,
          convertedFileBlob: blob,
          filename
        };

        setFileResults((prevResults) => {
          const updated = [...prevResults];
          updated[idx] = result;
          return updated;
        });
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

  async function preview(response) {
    if (!response?.checkResponseJson) return;

    setPreviewTableHeader(FINDINGS_TABLE_HEADER);
    setPreviewTableRows(buildFindingsRows(response.checkResponseJson));

    setPreviewCheckJson(response.checkResponseJson);
    setPreviewbpmnXml(response.originalModelXml);
    setPreviewFormSchema(null);
    setPreviewFormError("");
    setPreviewDiagramError(false);
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

  function openFormPreview(schema, errorMessage = "") {
    setPreviewFormSchema(schema);
    setPreviewFormError(errorMessage);
    setPreviewbpmnXml("");
    setPreviewCheckJson([]);
    setPreviewTableHeader([]);
    setPreviewTableRows([]);
    setPreviewDiagramError(false);
    setPreviewType("form");
    setIsPreviewOpen(true);
  }

  function previewForm(response) {
    const { schema, error } = parseFormSchema(response?.originalModelXml);
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
      <div className="whiteBox">
        <div>
          <div>
            <h2>Camunda Migration Analyzer &amp; Diagram Converter</h2>
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
              <h4>Add files</h4>
              <p>
                Upload BPMN or DMN models to analyze and convert, or Camunda Forms
                to convert.
              </p>
            </section>
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
            <p>
              Click the button below to analyze and convert your files.
            </p>

            <Form className="configBox" onSubmit={(e) => e.preventDefault()}>
              <h4>
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
              </h4>
            {showConfig && (
                <FormGroup legendText="">
                  <Checkbox
                    id="addDataMigrationExecutionListener"
                    labelText="Add data migration execution listener"
                    checked={configOptions.addDataMigrationExecutionListener}
                    helperText="Add a listener for use with the Camunda 7 Data Migrator."
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        addDataMigrationExecutionListener: checked,
                      }))
                    }
                  />
                  <TextInput
                    id="dataMigrationExecutionListenerJobType"
                    labelText="Execution listener job type"
                    value={configOptions.dataMigrationExecutionListenerJobType}
                    disabled={!configOptions.addDataMigrationExecutionListener}
                    onChange={(e) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        dataMigrationExecutionListenerJobType: e.target.value,
                      }))
                    }
                  />
                  <div className="form-spacer" />
                  <Checkbox
                    id="keepJobTypeBlank"
                    labelText="Keep job type blank"
                    checked={configOptions.keepJobTypeBlank}
                    helperText="Don't set a job type in process models."
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        keepJobTypeBlank: checked,
                      }))
                    }
                  />
                  <div className="form-spacer" />
                  <Checkbox
                    id="defaultJobTypeEnabled"
                    labelText="Always use default job type"
                    checked={configOptions.defaultJobTypeEnabled}
                    helperText="Always use the job type below instead of the delegate expression or class name."
                    disabled={configOptions.keepJobTypeBlank}
                    onChange={(e, { checked }) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        defaultJobTypeEnabled: checked,
                      }))
                    }
                  />
                  <TextInput
                    id="defaultJobType"
                    labelText="Default job type"
                    value={configOptions.defaultJobType}
                    disabled={configOptions.keepJobTypeBlank}
                    onChange={(e) =>
                      setConfigOptions((prev) => ({
                        ...prev,
                        defaultJobType: e.target.value,
                      }))
                    }
                  />
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

            <section>
              <h3>Converted files</h3>
              <p>
                Download each converted file or all of them as a ZIP. Use the eye
                icon to preview analysis findings for each file; BPMN files also
                render a diagram, and forms show a form preview.
              </p>
              {files.map((file, idx) => {
                const isForm = file.name.toLowerCase().endsWith(".form");
                return (
                <FileItem
                  key={file.name + "-" + idx}
                  name={file.name}
                  status={fileResults[idx].status}
                  isChecked={ fileResults[idx].checkResponseJson != null }
                  isConverted={fileResults[idx].convertedFileBlob != null}
                  previewAction={isForm ? () => previewForm(fileResults[idx]) : () => preview(fileResults[idx])}
                  previewTitle={isForm ? "Preview form" : undefined}
                  downloadAction={() => download(fileResults[idx])}
                  error={
                    !fileResults[idx].ok == "error" ? "File upload failure" : ""
                  }
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
              <h3>Analysis results</h3>
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

{isPreviewOpen && (
  <div className="modal-backdrop">
    <div className="modal">
      <div className="modal-header">
        <div className="left">
        <h2>Preview</h2>
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
            <div id="bpmnDiagram" className="diagram-container"></div>
          )}
          {(previewType === "other" || (previewType === "bpmn" && previewDiagramError)) && (
            <p style={{ marginTop: '1rem' }}>
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
            ? <p className="form-preview-error" role="alert">{previewFormError}</p>
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