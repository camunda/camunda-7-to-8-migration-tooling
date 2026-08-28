/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { describe, expect, it } from 'vitest';
import { buildFindingsRows, FINDINGS_TABLE_HEADER } from './findings';

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
