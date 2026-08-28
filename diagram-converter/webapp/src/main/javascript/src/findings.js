/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const FINDINGS_TABLE_HEADER = [
  { key: 'elementType', header: 'Element Type' },
  { key: 'elementId', header: 'Element ID' },
  { key: 'elementName', header: 'Element Name' },
  { key: 'severity', header: 'Severity' },
  { key: 'message', header: 'Message' },
  { key: 'link', header: 'Link' },
];

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
