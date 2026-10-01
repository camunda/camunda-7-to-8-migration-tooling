/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useLayoutEffect, useRef, useState } from "react";

import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@camunda/design-system";

import { SEVERITY_ORDER, getSeverityInfo, getSeverityRank } from "./findings";
import SeverityCell from "./SeverityCell";
import TableFilters from "./TableFilters";

const EMPTY_HIDDEN_SEVERITIES = new Set();
const UNKNOWN_SEVERITY = "Unknown";
const SORTABLE_COLUMNS = new Set([
  "elementType",
  "elementId",
  "elementName",
  "severity",
  "message",
]);

function normalizeSeverity(severity) {
  return severity || UNKNOWN_SEVERITY;
}

function compareSeverity(left, right, direction) {
  const leftRank = getSeverityRank(normalizeSeverity(left));
  const rightRank = getSeverityRank(normalizeSeverity(right));
  const leftIsUnknown = leftRank === SEVERITY_ORDER.length;
  const rightIsUnknown = rightRank === SEVERITY_ORDER.length;

  if (leftIsUnknown !== rightIsUnknown) {
    return leftIsUnknown ? 1 : -1;
  }

  const difference = leftRank - rightRank;
  return direction === "desc" ? -difference : difference;
}

function compareRows(left, right, sortKey, direction) {
  if (sortKey === "severity") {
    return compareSeverity(left.severity, right.severity, direction);
  }

  const difference = String(left[sortKey] ?? "").localeCompare(
    String(right[sortKey] ?? ""),
    undefined,
    { numeric: true, sensitivity: "base" }
  );
  return direction === "desc" ? -difference : difference;
}

// Renders the findings table for a previewed file, plus the controls needed
// to make large or mixed-severity result sets actionable:
//  - search, severity filters and sorting for large result sets,
//  - a short legend translating the raw analyzer severity codes into plain
//    language, and
//  - a "showing X of Y" summary so the current filter state stays visible.
export default function FindingsSection({
  header,
  rows,
  onSelectElement,
  selectedElementId,
  hiddenSeverities: controlledHiddenSeverities,
  onHiddenSeveritiesChange,
}) {
  const [tableState, setTableState] = useState(() => ({
    rows,
    searchValue: "",
    hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
    sortKey: "severity",
    sortDirection: "asc",
  }));
  const isFilterControlled = controlledHiddenSeverities !== undefined;
  const tableWrapperRef = useRef(null);
  const currentState =
    tableState.rows === rows
      ? tableState
      : {
          rows,
          searchValue: "",
          hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
          sortKey: "severity",
          sortDirection: "asc",
        };
  const { searchValue, sortKey, sortDirection } = currentState;
  const hiddenSeverities = isFilterControlled
    ? controlledHiddenSeverities
    : currentState.hiddenSeverities;

  const severityCounts = [...rows.reduce((counts, row) => {
    const severity = normalizeSeverity(row.severity);
    counts.set(severity, (counts.get(severity) || 0) + 1);
    return counts;
  }, new Map())]
    .map(([severity, count]) => ({ severity, count }))
    .sort((a, b) => getSeverityRank(a.severity) - getSeverityRank(b.severity));

  const severityOptions = severityCounts.map(({ severity, count }) => ({
    value: severity,
    label: getSeverityInfo(severity).label,
    code: severity,
    count,
  }));
  const query = searchValue.trim().toLowerCase();
  const matchesSearch = (row) =>
    query.length === 0 ||
    header
      .map(({ key }) => {
        if (key === "severity") {
          const severity = normalizeSeverity(row.severity);
          return `${getSeverityInfo(severity).label} ${severity}`;
        }
        return String(row[key] ?? "");
      })
      .join(" ")
      .toLowerCase()
      .includes(query);
  const isFilteredOut = (row) =>
    !matchesSearch(row) ||
    hiddenSeverities.has(normalizeSeverity(row.severity));
  const selectedFindingsAreFiltered =
    !!selectedElementId &&
    rows.some(
      (row) =>
        row.elementId === selectedElementId && isFilteredOut(row)
    );
  const visibleRows = rows
    .map((row, index) => ({ row, index }))
    .filter(({ row }) => {
      const isSelected =
        !!selectedElementId && row.elementId === selectedElementId;
      return isSelected || !isFilteredOut(row);
    })
    .sort(
      (left, right) =>
        compareRows(left.row, right.row, sortKey, sortDirection) ||
        left.index - right.index
    )
    .map(({ row }) => row);

  useLayoutEffect(() => {
    if (!selectedElementId) return;

    const selectedRows = [
      ...(tableWrapperRef.current?.querySelectorAll("tr[data-finding-element-id]") ?? []),
    ].filter((row) => row.dataset.findingElementId === selectedElementId);
    selectedRows.forEach((row) => row.scrollIntoView?.({ block: "nearest" }));
  }, [rows, selectedElementId]);

  if (rows.length === 0) {
    return (
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>No findings for this file.</p>
    );
  }

  function updateTableState(update) {
    setTableState((previous) => {
      const base =
        previous.rows === rows
          ? previous
          : {
              rows,
              searchValue: "",
              hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
              sortKey: "severity",
              sortDirection: "asc",
            };
      return { ...base, ...update(base) };
    });
  }

  function toggleSeverity(severity) {
    if (isFilterControlled) {
      const next = new Set(controlledHiddenSeverities);
      if (next.has(severity)) {
        next.delete(severity);
      } else {
        next.add(severity);
      }
      onHiddenSeveritiesChange?.(next);
      return;
    }

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
    if (isFilterControlled) {
      onHiddenSeveritiesChange?.(EMPTY_HIDDEN_SEVERITIES);
    }
  }

  return (
    <>
      <h3>Findings</h3>
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginBottom: '0.75rem' }}>
        Elements in this file that need attention during migration. Each row describes one finding — its location, severity, and a message explaining what to address.
        {onSelectElement && ' Select an element ID to locate it in the diagram, or select a diagram element to locate its findings here.'}
      </p>

      <TableFilters
        tableId="findings-table"
        searchLabel="Search findings"
        searchPlaceholder="Search element, severity, or message"
        searchValue={searchValue}
        onSearchChange={(value) =>
          updateTableState(() => ({ searchValue: value }))
        }
        severityLabel="Filter findings by severity"
        severityOptions={severityOptions}
        hiddenSeverities={hiddenSeverities}
        onToggleSeverity={toggleSeverity}
        visibleCount={visibleRows.length}
        totalCount={rows.length}
        resultLabel="finding"
        onClearFilters={clearFilters}
      />

      <details className="severity-legend">
        <summary>What do these severities mean?</summary>
        <dl>
          {severityCounts.map(({ severity }) => {
            const info = getSeverityInfo(severity);
            return (
              <div key={severity} className="severity-legend-item">
                <dt>{info.label} <span className="severity-chip-code">{severity}</span></dt>
                <dd>{info.description}</dd>
              </div>
            );
          })}
        </dl>
      </details>

      {selectedFindingsAreFiltered && (
        <p className="table-filter-summary">
          Selected element findings remain visible while filters are active.
        </p>
      )}
      {visibleRows.length === 0 ? (
        <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>
          No findings match the current filters.
        </p>
      ) : (
        <div
          className="analysisTableWrapper"
          role="region"
          aria-label="Scrollable findings table"
          tabIndex={0}
          ref={tableWrapperRef}
        >
          <Table
            id="findings-table"
            className="analysis-table"
            aria-label="Findings for this file"
          >
            <TableHeader>
              <TableRow>
                {header.map((h) => {
                  const isSortable = SORTABLE_COLUMNS.has(h.key);
                  const ariaSort =
                    sortKey === h.key
                      ? sortDirection === "asc"
                        ? "ascending"
                        : "descending"
                      : "none";

                  return (
                    <TableHead
                      key={h.key}
                      aria-label={h.header}
                      aria-sort={isSortable ? ariaSort : undefined}
                    >
                      {isSortable ? (
                        <button
                          type="button"
                          className="table-sort-button"
                          aria-label={`Sort by ${h.header}`}
                          onClick={() => sortBy(h.key)}
                        >
                          {h.header}
                        </button>
                      ) : (
                        h.header
                      )}
                    </TableHead>
                  );
                })}
              </TableRow>
            </TableHeader>
            <TableBody>
              {visibleRows.map((row) => {
                const isLinkable =
                  !!onSelectElement && !!row.elementId && row.elementId !== '-';
                return (
                  <TableRow
                    key={row.id}
                    aria-selected={isLinkable ? selectedElementId === row.elementId : undefined}
                    data-finding-element-id={isLinkable ? row.elementId : undefined}
                  >
                    {header.map((h) => {
                      const value = row[h.key];
                      if (h.key === 'elementId' && isLinkable) {
                        return (
                          <TableCell key={`${row.id}-${h.key}`}>
                            <button
                              type="button"
                              className="findingElementLink"
                              onClick={() => onSelectElement(row.elementId)}
                            >
                              {value}
                            </button>
                          </TableCell>
                        );
                      }
                      if (h.key === "severity") {
                        return (
                          <TableCell key={`${row.id}-${h.key}`}>
                            <SeverityCell severity={value} />
                          </TableCell>
                        );
                      }
                      return (
                        <TableCell key={`${row.id}-${h.key}`}>
                          {h.key === 'link' ? (
                            value ? (
                              <a
                                href={value}
                                target="_blank"
                                rel="noopener noreferrer"
                                aria-label={`Open finding documentation: ${value}`}
                              >
                                Open
                              </a>
                            ) : '-'
                          ) : value}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  );
}
