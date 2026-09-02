/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Analyzer severities, ranked from most to least urgent. This is the single
// source of truth for severity ordering across the findings table, the
// severity filter/legend, the per-file findings badge and the diagram/form
// preview highlighting — keep them in sync by importing from here rather than
// duplicating the list.
export const SEVERITY_ORDER = ['WARNING', 'TASK', 'REVIEW', 'INFO'];

// Plain-language labels and explanations for each raw analyzer severity code,
// matching the terminology from the migration-analyzer documentation
// (https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/).
// Raw codes remain visible in the UI as secondary detail so users who already
// know the analyzer vocabulary (e.g. from downloaded reports) can cross-reference them.
export const SEVERITY_INFO = {
  WARNING: {
    label: 'No direct mapping',
    description: "A Camunda 7 concept can't be directly mapped to a Camunda 8 equivalent. Review the Camunda 8 roadmap or explore a workaround.",
  },
  TASK: {
    label: 'Manual action required',
    description: 'Manual changes are required to make this element work in Camunda 8.',
  },
  REVIEW: {
    label: 'Verify after conversion',
    description: 'The conversion changed an expression or attribute. Verify that the intended behavior is unchanged.',
  },
  INFO: {
    label: 'No action needed',
    description: 'This was converted automatically and needs no follow-up.',
  },
};

// Returns the plain-language label/description for a severity code, falling
// back to the raw code itself for any value the UI doesn't recognize.
export function getSeverityInfo(severity) {
  return SEVERITY_INFO[severity] || { label: severity || 'Unknown', description: '' };
}

export const FINDINGS_TABLE_HEADER = [
  { key: 'elementType', header: 'Element type' },
  { key: 'elementId', header: 'Element ID' },
  { key: 'elementName', header: 'Element name' },
  { key: 'severity', header: 'Severity' },
  { key: 'message', header: 'Message' },
  { key: 'link', header: 'Link' },
];

// Maps each severity to a CSS class suffix (used for both diagram highlight
// classes, e.g. `highlight-warning`, and the findings count badge). Kept as
// a single lookup so severity styling can't drift between the two.
export function getSeverityStyleKey(severity) {
  return SEVERITY_ORDER.includes(severity) ? severity.toLowerCase() : 'info';
}

// Returns the most severe value found in a list of items exposing a
// `severity` field (either raw check-response messages or findings rows
// built by buildFindingsRows). Returns null when no recognizable severity
// is present, e.g. an empty findings list.
export function getHighestSeverity(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return null;
  }

  let mostSevere = null;
  for (const item of items) {
    const severityIndex = SEVERITY_ORDER.indexOf(item?.severity);
    if (severityIndex === -1) continue;
    const mostSevereIndex = mostSevere === null ? -1 : SEVERITY_ORDER.indexOf(mostSevere);
    if (mostSevereIndex === -1 || severityIndex < mostSevereIndex) {
      mostSevere = item.severity;
    }
  }
  return mostSevere;
}

// Flattens the /check response (List<DiagramCheckResult>) into table rows,
// one row per finding message across all result items.
export function buildFindingsRows(checkResponseJson) {
  if (!Array.isArray(checkResponseJson)) {
    return [];
  }

  return checkResponseJson.flatMap((item, itemIdx) => {
    if (!item || typeof item !== 'object' || !Array.isArray(item.results)) {
      return [];
    }

    return item.results.flatMap((element, elementIdx) => {
      if (!element || typeof element !== 'object' || !Array.isArray(element.messages)) {
        return [];
      }

      return element.messages.flatMap((message, msgIdx) => {
        if (!message || typeof message !== 'object') {
          return [];
        }

        return [{
          id: `${itemIdx}-${elementIdx}-${msgIdx}`,
          elementType: element.elementType || '-',
          elementId: element.elementId || '-',
          elementName: element.elementName || '(unnamed)',
          severity: message.severity,
          message: message.message,
          link: message.link || null,
        }];
      });
    });
  });
}
