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

import { SEVERITY_ORDER, getSeverityInfo } from "./findings";

function severityRank(severity) {
  const index = SEVERITY_ORDER.indexOf(severity);
  return index === -1 ? SEVERITY_ORDER.length : index;
}

const EMPTY_HIDDEN_SEVERITIES = new Set();
const UNKNOWN_SEVERITY = "Unknown";

function normalizeSeverity(severity) {
  return severity || UNKNOWN_SEVERITY;
}

// Renders the findings table for a previewed file, plus the controls needed
// to make large or mixed-severity result sets actionable:
//  - a severity filter so users can show only the findings that matter to
//    them and return to the full list without losing their place,
//  - a short legend translating the raw analyzer severity codes into plain
//    language, and
//  - a "showing X of Y" summary so the current filter state stays visible.
export default function FindingsSection({
  header,
  rows,
  onSelectElement,
  selectedElementId,
}) {
  const [filterState, setFilterState] = useState(() => ({
    rows,
    hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
  }));
  const hiddenSeverities =
    filterState.rows === rows ? filterState.hiddenSeverities : EMPTY_HIDDEN_SEVERITIES;

  if (rows.length === 0) {
    return (
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>No findings for this file.</p>
    );
  }

  const severityCounts = [...rows.reduce((counts, row) => {
    const severity = normalizeSeverity(row.severity);
    counts.set(severity, (counts.get(severity) || 0) + 1);
    return counts;
  }, new Map())]
    .map(([severity, count]) => ({ severity, count }))
    .sort((a, b) => severityRank(a.severity) - severityRank(b.severity));

  const sortedRows = [...rows].sort(
    (a, b) => severityRank(normalizeSeverity(a.severity)) - severityRank(normalizeSeverity(b.severity))
  );
  const visibleRows = sortedRows.filter(
    (row) => !hiddenSeverities.has(normalizeSeverity(row.severity))
  );
  const isFiltered = hiddenSeverities.size > 0;

  function toggleSeverity(severity) {
    setFilterState((prev) => {
      const previousHiddenSeverities =
        prev.rows === rows ? prev.hiddenSeverities : EMPTY_HIDDEN_SEVERITIES;
      const next = new Set(previousHiddenSeverities);
      if (next.has(severity)) {
        next.delete(severity);
      } else {
        next.add(severity);
      }
      return {
        rows,
        hiddenSeverities: next,
      };
    });
  }

  function showAll() {
    setFilterState({
      rows,
      hiddenSeverities: EMPTY_HIDDEN_SEVERITIES,
    });
  }

  return (
    <>
      <h3>Findings</h3>
      <p style={{ color: 'var(--neutral-foreground-subtle)', marginBottom: '0.75rem' }}>
        Elements in this file that need attention during migration. Each row describes one finding — its location, severity, and a message explaining what to address.
        {onSelectElement && ' Select an element ID to locate it in the diagram.'}
      </p>

      {severityCounts.length > 1 && (
        <div className="severity-filter" role="group" aria-label="Filter findings by severity">
          {severityCounts.map(({ severity, count }) => {
            const info = getSeverityInfo(severity);
            const isActive = !hiddenSeverities.has(severity);
            return (
              <button
                key={severity}
                type="button"
                className="severity-chip"
                data-severity={severity}
                aria-pressed={isActive}
                onClick={() => toggleSeverity(severity)}
              >
                {info.label} <span className="severity-chip-code">{severity}</span> ({count})
              </button>
            );
          })}
        </div>
      )}

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

      {isFiltered && (
        <p className="severity-filter-summary">
          Showing {visibleRows.length} of {rows.length} finding{rows.length !== 1 ? 's' : ''}.{' '}
          <button type="button" className="link-button" onClick={showAll}>
            Show all findings
          </button>
        </p>
      )}

      {visibleRows.length === 0 ? (
        <p style={{ color: 'var(--neutral-foreground-subtle)', marginTop: '1rem' }}>
          No findings match the selected severities.
        </p>
      ) : (
        <div className="analysisTableWrapper">
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
              {visibleRows.map((row) => {
                const isLinkable =
                  !!onSelectElement && !!row.elementId && row.elementId !== '-';
                return (
                  <TableRow
                    key={row.id}
                    aria-selected={isLinkable ? selectedElementId === row.elementId : undefined}
                  >
                    {header.map((h) => {
                      const value = row[h.key];
                      const severity = normalizeSeverity(value);
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
                          ) : h.key === 'severity' ? (
                            <span className="severity-cell">
                              <span className="severity-cell-label">{getSeverityInfo(severity).label}</span>
                              <span className="severity-cell-code"> ({severity})</span>
                            </span>
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
