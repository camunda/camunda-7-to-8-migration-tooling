/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useState, useEffect } from "react";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';

import { Table } from "./Table";

export default function PaginatedTable({
  columns,
  data,
  totalCount,
  loading,
  onPageChange,
  initialPageSize = 10,
  pageSizeOptions = [5, 10, 20, 30, 40, 50]
}) {
  // Simple state for pagination
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSize, setPageSize] = useState(initialPageSize);

  // React Table instance
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: Math.max(1, Math.ceil(totalCount / pageSize)),
  });

  // Effect to notify parent component when pagination changes
  useEffect(() => {
    // Only trigger the callback if we have a valid onPageChange function
    // AND there's data to paginate (avoid unnecessary API calls when count is 0)
    if (onPageChange && typeof onPageChange === 'function' && (totalCount > 0 || pageIndex === 0)) {
      onPageChange(pageIndex, pageSize);
    }
  }, [pageIndex, pageSize, totalCount]);

  // Calculate max page based on current data, ensure it's at least 0
  const maxPage = totalCount > 0 ? Math.max(0, Math.ceil(totalCount / pageSize) - 1) : 0;

  // Handle page size change
  function changePageSize(newSize) {
    setPageSize(Number(newSize));
    setPageIndex(0); // Reset to first page when changing page size
  }

  return (
    <>
      <Table
        head={
          <>
            {columns.map((column, i) => (
              <Table.Head key={i} style={{ width: column.size ? `${column.size}px` : 'auto' }}>
                {column.header}
              </Table.Head>
            ))}
          </>
        }
      >
        {data.length === 0 ? (
          <Table.Row>
            <Table.Cell colSpan={columns.length} style={{ textAlign: 'center' }}>
              {loading ? 'Loading...' : 'No data available'}
            </Table.Cell>
          </Table.Row>
        ) : (
          data.map((row, rowIndex) => (
            <Table.Row key={rowIndex}>
              {columns.map((column, colIndex) => (
                <Table.Cell key={colIndex} style={{ width: column.size ? `${column.size}px` : 'auto' }}>
                  {column.cell({ row: { original: row }, getValue: () => row[column.accessorKey] })}
                </Table.Cell>
              ))}
            </Table.Row>
          ))
        )}
      </Table>

      {/* Pagination Controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '20px' }}>
        <button
          onClick={() => setPageIndex(0)}
          disabled={pageIndex === 0}
          style={{ padding: '5px 10px' }}
        >
          {'<<'}
        </button>
        <button
          onClick={() => setPageIndex(Math.max(0, pageIndex - 1))}
          disabled={pageIndex === 0}
          style={{ padding: '5px 10px' }}
        >
          {'<'}
        </button>
        <button
          onClick={() => setPageIndex(Math.min(maxPage, pageIndex + 1))}
          disabled={pageIndex >= maxPage}
          style={{ padding: '5px 10px' }}
        >
          {'>'}
        </button>
        <button
          onClick={() => setPageIndex(maxPage)}
          disabled={pageIndex >= maxPage}
          style={{ padding: '5px 10px' }}
        >
          {'>>'}
        </button>
        <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
          <div>Page</div>
          <strong>
            {pageIndex + 1} of{' '}
            {Math.max(1, Math.ceil(totalCount / pageSize))}
          </strong>
        </span>
        <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
          | Go to page:
          <input
            type="number"
            value={pageIndex + 1}
            onChange={e => {
              const page = e.target.value ? Number(e.target.value) - 1 : 0;
              setPageIndex(Math.max(0, Math.min(maxPage, page)));
            }}
            style={{ width: '60px', padding: '2px 5px' }}
          />
        </span>
        <select
          value={pageSize}
          onChange={e => changePageSize(e.target.value)}
          style={{ padding: '2px 5px' }}
        >
          {pageSizeOptions.map(size => (
            <option key={size} value={size}>
              Show {size}
            </option>
          ))}
        </select>
        {loading && <span>Loading...</span>}
      </div>
    </>
  );
}
