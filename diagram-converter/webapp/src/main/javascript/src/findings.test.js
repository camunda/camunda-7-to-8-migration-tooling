/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { describe, expect, it } from 'vitest';
import {
  buildFindingsRows,
  FINDINGS_TABLE_HEADER,
  SEVERITY_ORDER,
  getHighestSeverity,
  getSeverityStyleKey,
} from './findings';

describe('buildFindingsRows', () => {
  it('returns no rows for empty or missing input', () => {
    expect(buildFindingsRows(null)).toEqual([]);
    expect(buildFindingsRows(undefined)).toEqual([]);
    expect(buildFindingsRows([])).toEqual([]);
    expect(buildFindingsRows([{ results: [] }])).toEqual([]);
    expect(buildFindingsRows([{}])).toEqual([]);
  });

  it('returns no rows for malformed non-array result data', () => {
    expect(buildFindingsRows({ results: [] })).toEqual([]);
    expect(buildFindingsRows('not an array')).toEqual([]);
    expect(buildFindingsRows([{ results: {} }])).toEqual([]);
    expect(buildFindingsRows([{ results: [{ messages: {} }] }])).toEqual([]);
    expect(buildFindingsRows([{ results: [null, { messages: [null] }] }])).toEqual([]);
  });

  it('flattens findings across multiple result items and elements', () => {
    const rows = buildFindingsRows([
      {
        results: [
          {
            elementId: 'task1',
            elementName: 'Task One',
            elementType: 'bpmn:ServiceTask',
            messages: [
              { severity: 'WARNING', message: 'first', link: 'https://example.com/1' },
              { severity: 'INFO', message: 'second', link: null },
            ],
          },
        ],
      },
      {
        results: [
          {
            elementId: 'task2',
            elementName: null,
            elementType: 'bpmn:UserTask',
            messages: [{ severity: 'TASK', message: 'third', link: 'https://example.com/2' }],
          },
        ],
      },
    ]);

    expect(rows).toHaveLength(3);
    expect(rows.map((r) => r.message)).toEqual(['first', 'second', 'third']);
    expect(rows[0]).toMatchObject({
      id: '0-0-0',
      elementId: 'task1',
      elementName: 'Task One',
      elementType: 'bpmn:ServiceTask',
      severity: 'WARNING',
      link: 'https://example.com/1',
    });
    // ids are unique across items, elements and messages
    expect(new Set(rows.map((r) => r.id)).size).toBe(3);
  });

  it('renders fallbacks for findings without element id, name or type', () => {
    const rows = buildFindingsRows([
      {
        results: [
          {
            elementId: null,
            elementName: null,
            elementType: null,
            messages: [{ severity: 'REVIEW', message: 'form finding' }],
          },
        ],
      },
    ]);

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      elementId: '-',
      elementType: '-',
      elementName: '(unnamed)',
      link: null,
    });
  });

  it('tolerates elements without a messages array', () => {
    const rows = buildFindingsRows([{ results: [{ elementId: 'x' }] }]);
    expect(rows).toEqual([]);
  });
});

describe('FINDINGS_TABLE_HEADER', () => {
  it('covers severity, element identity, message and link', () => {
    expect(FINDINGS_TABLE_HEADER.map((h) => h.key)).toEqual([
      'elementType',
      'elementId',
      'elementName',
      'severity',
      'message',
      'link',
    ]);
  });
});

describe('getHighestSeverity', () => {
  it('returns null for empty or missing input', () => {
    expect(getHighestSeverity([])).toBeNull();
    expect(getHighestSeverity(null)).toBeNull();
    expect(getHighestSeverity(undefined)).toBeNull();
  });

  it('returns null when no item has a recognizable severity', () => {
    expect(getHighestSeverity([{ severity: 'UNKNOWN' }, { severity: null }])).toBeNull();
  });

  it('picks the most severe value regardless of input order', () => {
    expect(getHighestSeverity([{ severity: 'INFO' }, { severity: 'WARNING' }])).toBe('WARNING');
    expect(getHighestSeverity([{ severity: 'WARNING' }, { severity: 'INFO' }])).toBe('WARNING');
    expect(getHighestSeverity([{ severity: 'REVIEW' }, { severity: 'TASK' }])).toBe('TASK');
  });

  it('ignores unrecognized severities mixed with recognized ones', () => {
    expect(getHighestSeverity([{ severity: 'BOGUS' }, { severity: 'REVIEW' }])).toBe('REVIEW');
  });

  it('is consistent for every severity in SEVERITY_ORDER taken alone', () => {
    // Litmus test for the severity-ordering defect category: every entry in
    // SEVERITY_ORDER must round-trip through getHighestSeverity unchanged
    // when it's the only finding, not just the ones exercised above.
    for (const severity of SEVERITY_ORDER) {
      expect(getHighestSeverity([{ severity }])).toBe(severity);
    }
  });
});

describe('getSeverityStyleKey', () => {
  it('lowercases each known severity for use as a CSS class suffix', () => {
    expect(getSeverityStyleKey('WARNING')).toBe('warning');
    expect(getSeverityStyleKey('TASK')).toBe('task');
    expect(getSeverityStyleKey('REVIEW')).toBe('review');
    expect(getSeverityStyleKey('INFO')).toBe('info');
  });

  it('falls back to info for unrecognized or missing severities', () => {
    expect(getSeverityStyleKey('BOGUS')).toBe('info');
    expect(getSeverityStyleKey(undefined)).toBe('info');
    expect(getSeverityStyleKey(null)).toBe('info');
  });
});
