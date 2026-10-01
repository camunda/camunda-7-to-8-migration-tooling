/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@camunda/design-system";
import { Check, Loader2 } from "lucide-react";

import { getSeverityInfo, getSeverityRank, SEVERITY_ORDER } from "./findings";
import { FileItemActions, FileItemError } from "./FileItem";
import SeverityCell from "./SeverityCell";
import TableFilters from "./TableFilters";

const EMPTY_HIDDEN_SEVERITIES = new Set();
const NO_FINDINGS = "__NO_FINDINGS__";
const NOT_ANALYZED = "__NOT_ANALYZED__";
const COLUMNS = [
  { key: "name", label: "File name" },
  { key: "findingCount", label: "Finding count" },
  { key: "severity", label: "Severity" },
];

function normalizeSeverity(severity) {
  return severity || "Unknown";
}

function severityFilterValue(row) {
  if (!row.isChecked) {
    return NOT_ANALYZED;
  }
  if (row.findingCount === 0) {
    return NO_FINDINGS;
  }
  return normalizeSeverity(row.highestSeverity);
}

function severityRank(value) {
  if (value === NOT_ANALYZED) {
    return SEVERITY_ORDER.length + 1;
  }
  if (value === NO_FINDINGS) {
    return SEVERITY_ORDER.length + 2;
  }
  return getSeverityRank(value);
}

function severityDescription(value) {
  if (value === NO_FINDINGS) {
    return { label: "No findings", code: null };
  }
  if (value === NOT_ANALYZED) {
    return { label: "Not analyzed", code: null };
  }
  return { label: getSeverityInfo(value).label, code: value };
}

function fileStatusLabel(row) {
  switch (row.status) {
    case "uploading":
      return row.isChecked ? "Converting…" : "Analyzing…";
    case "success":
      return "Success";
    case "error":
      return "Failed";
    default:
      return "Unknown";
  }
}

function compareSeverity(left, right, direction) {
  const leftValue = severityFilterValue(left);
  const rightValue = severityFilterValue(right);
  const leftRank = severityRank(leftValue);
  const rightRank = severityRank(rightValue);

  if (leftRank >= SEVERITY_ORDER.length || rightRank >= SEVERITY_ORDER.length) {
    return leftRank - rightRank;
  }

  const difference = leftRank - rightRank;
  return direction === "desc" ? -difference : difference;
}

function compareRows(left, right, sortKey, direction) {
  if (sortKey === "severity") {
    return compareSeverity(left, right, direction);
  }
  if (sortKey === "findingCount") {
    if (left.isChecked !== right.isChecked) {
      return left.isChecked ? -1 : 1;
    }
    const difference = left.findingCount - right.findingCount;
    return direction === "desc" ? -difference : difference;
  }

  const difference = String(left.name ?? "").localeCompare(
    String(right.name ?? ""),
    undefined,
    { numeric: true, sensitivity: "base" }
  );
  return direction === "desc" ? -difference : difference;
}

function initialState(resetKey) {
  return {
    resetKey,
    searchValue: "",
    hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
    sortKey: "severity",
    sortDirection: "asc",
  };
}

export default function FileResultsTable({ rows, resetKey = rows }) {
  const [tableState, setTableState] = useState(() => initialState(resetKey));
  const currentState =
    tableState.resetKey === resetKey ? tableState : initialState(resetKey);
  const { searchValue, hiddenSeverities, sortKey, sortDirection } = currentState;

  function updateTableState(update) {
    setTableState((previous) => {
      const base =
        previous.resetKey === resetKey ? previous : initialState(resetKey);
      return { ...base, ...update(base) };
    });
  }

  function toggleSeverity(severity) {
    updateTableState((previous) => {
      const next = new Set(previous.hiddenSeverities);
      if (next.has(severity)) {
        next.delete(severity);
      } else {
        next.add(severity);
      }
      return { hiddenSeverities: next };
    });
  }

  function sortBy(key) {
    updateTableState((previous) => ({
      sortKey: key,
      sortDirection:
        previous.sortKey === key && previous.sortDirection === "asc"
          ? "desc"
          : "asc",
    }));
  }

  function clearFilters() {
    updateTableState(() => ({
      searchValue: "",
      hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
    }));
  }

  const severityCounts = rows.reduce((counts, row) => {
    const severity = severityFilterValue(row);
    counts.set(severity, (counts.get(severity) || 0) + 1);
    return counts;
  }, new Map());
  const severityValues = new Set([
    ...severityCounts.keys(),
    ...hiddenSeverities,
  ]);
  const severityOptions = [...severityValues]
    .map((value) => ({
      value,
      ...severityDescription(value),
      count: severityCounts.get(value) || 0,
    }))
    .sort((left, right) => severityRank(left.value) - severityRank(right.value));

  const query = searchValue.trim().toLocaleLowerCase();
  const visibleRows = rows
    .map((row, index) => ({ row, index }))
    .filter(({ row }) => {
      const matchesSearch =
        query.length === 0 ||
        String(row.name ?? "").toLocaleLowerCase().includes(query);
      return matchesSearch && !hiddenSeverities.has(severityFilterValue(row));
    })
    .sort(
      (left, right) =>
        compareRows(left.row, right.row, sortKey, sortDirection) ||
        left.index - right.index
    )
    .map(({ row }) => row);

  if (rows.length === 0) {
    return <p className="table-empty-state">No file results available.</p>;
  }

  return (
    <section className="file-results-section" aria-label="Batch file results">
      <TableFilters
        tableId="file-results-table"
        searchLabel="Search files"
        searchPlaceholder="Search by filename"
        searchValue={searchValue}
        onSearchChange={(value) =>
          updateTableState(() => ({ searchValue: value }))
        }
        severityLabel="Filter files by severity"
        severityOptions={severityOptions}
        hiddenSeverities={hiddenSeverities}
        onToggleSeverity={toggleSeverity}
        visibleCount={visibleRows.length}
        totalCount={rows.length}
        resultLabel="file"
        onClearFilters={clearFilters}
      />

      {visibleRows.length === 0 ? (
        <p className="table-empty-state">
          No files match the current filters.
        </p>
      ) : (
        <div
          className="analysisTableWrapper"
          role="region"
          aria-label="Scrollable file results table"
          tabIndex={0}
        >
          <Table
            id="file-results-table"
            className="analysis-table file-results-table"
            aria-label="Batch file results"
          >
            <TableHeader>
              <TableRow>
                {COLUMNS.map(({ key, label }) => (
                  <TableHead
                    key={key}
                    aria-label={label}
                    aria-sort={
                      sortKey === key
                        ? sortDirection === "asc"
                          ? "ascending"
                          : "descending"
                        : "none"
                    }
                  >
                    <button
                      type="button"
                      className="table-sort-button"
                      aria-label={`Sort by ${label}`}
                      onClick={() => sortBy(key)}
                    >
                      {label}
                    </button>
                  </TableHead>
                ))}
                <TableHead>Status</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visibleRows.map((row) => (
                <TableRow key={row.id} className="file-result-row">
                  <TableCell>
                    <div className="file-result-name-cell">
                      {row.status === "success" && (
                        <Check
                          aria-hidden="true"
                          className="file-result-success"
                        />
                      )}
                      <span className="file-result-name" title={row.name}>
                        {row.name}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    {row.isChecked
                      ? `${row.findingCount} finding${row.findingCount === 1 ? "" : "s"}`
                      : "—"}
                  </TableCell>
                  <TableCell>
                    {!row.isChecked ? (
                      <span className="file-result-no-severity">Not analyzed</span>
                    ) : row.findingCount === 0 ? (
                      <span className="file-result-no-severity">No findings</span>
                    ) : (
                      <SeverityCell severity={row.highestSeverity} />
                    )}
                  </TableCell>
                  <TableCell>
                    <span
                      className="file-result-status"
                      role={row.status === "uploading" ? "status" : undefined}
                    >
                      {row.status === "uploading" && (
                        <Loader2
                          aria-hidden="true"
                          className="size-4 animate-spin text-primary-action-default"
                        />
                      )}
                      {fileStatusLabel(row)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <div className="file-result-action-cell">
                      <div className="file-result-actions">
                        <FileItemActions
                          name={row.name}
                          error={row.error}
                          isChecked={row.isChecked}
                          isConverted={row.isConverted}
                          downloadAction={row.downloadAction}
                          previewAction={row.previewAction}
                          previewTitle={row.previewTitle}
                        />
                      </div>
                      {row.error && (
                        <FileItemError
                          error={row.error}
                          onRetry={row.onRetry}
                          className="FileItemError file-result-error"
                        />
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  );
}
