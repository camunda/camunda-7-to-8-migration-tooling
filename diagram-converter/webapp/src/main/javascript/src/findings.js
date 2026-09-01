/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const FINDINGS_TABLE_HEADER = [
  { key: 'elementType', header: 'Element type' },
  { key: 'elementId', header: 'Element ID' },
  { key: 'elementName', header: 'Element name' },
  { key: 'severity', header: 'Severity' },
  { key: 'message', header: 'Message' },
  { key: 'link', header: 'Link' },
];

// Ordered from most to least severe. Shared by the findings table, the
// per-file findings badge and the diagram/form preview highlighting so all
// three surfaces agree on what "most severe" means for a given file.
export const SEVERITY_ORDER = ['WARNING', 'TASK', 'REVIEW', 'INFO'];

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
