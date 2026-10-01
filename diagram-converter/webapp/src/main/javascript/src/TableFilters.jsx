/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useId } from "react";
import { Input } from "@camunda/design-system";

function describeSeverity(option) {
  return option.code ? `${option.label} (${option.code})` : option.label;
}

export default function TableFilters({
  searchLabel,
  searchPlaceholder,
  searchValue,
  onSearchChange,
  tableId,
  severityLabel,
  severityOptions,
  hiddenSeverities,
  onToggleSeverity,
  visibleCount,
  totalCount,
  resultLabel,
  onClearFilters,
}) {
  const id = useId();
  const searchInputId = `${id}-search`;
  const hasSearch = searchValue.trim().length > 0;
  const excludedSeverities = severityOptions.filter(({ value }) =>
    hiddenSeverities.has(value)
  );
  const activeFilters = [];

  if (hasSearch) {
    activeFilters.push(`Search "${searchValue.trim()}"`);
  }
  if (excludedSeverities.length > 0) {
    activeFilters.push(
      `Excluded severities: ${excludedSeverities.map(describeSeverity).join(", ")}`
    );
  }

  const hasActiveFilters = hasSearch || hiddenSeverities.size > 0;
  const resultNoun = `${resultLabel}${totalCount === 1 ? "" : "s"}`;

  return (
    <>
      <div className="table-filter-controls">
        <div className="table-search-control">
          <label htmlFor={searchInputId}>{searchLabel}</label>
          <Input
            id={searchInputId}
            className="table-search-input"
            type="search"
            value={searchValue}
            placeholder={searchPlaceholder}
            aria-controls={visibleCount > 0 ? tableId : undefined}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </div>

        {severityOptions.length > 1 && (
          <fieldset className="severity-filter-control">
            <legend>{severityLabel}</legend>
            <div className="severity-filter">
              {severityOptions.map(({ value, label, code, count }) => {
                const isActive = !hiddenSeverities.has(value);

                return (
                  <button
                    key={value}
                    type="button"
                    className="severity-chip"
                    data-severity={value}
                    aria-pressed={isActive}
                    onClick={() => onToggleSeverity(value)}
                  >
                    <span>{label}</span>
                    {code && (
                      <>
                        {" "}
                        <span className="severity-chip-code">{code}</span>
                      </>
                    )}
                    {" "}
                    <span className="severity-chip-count">({count})</span>
                  </button>
                );
              })}
            </div>
          </fieldset>
        )}
      </div>

      <p className="table-filter-summary" role="status" aria-live="polite">
        Showing {visibleCount} of {totalCount} {resultNoun}.
        {hasActiveFilters && (
          <>
            {" "}
            <span className="active-filter-description">
              Active filters: {activeFilters.join("; ")}.
            </span>{" "}
            <button
              type="button"
              className="link-button"
              onClick={onClearFilters}
            >
              Clear filters
            </button>
          </>
        )}
      </p>
    </>
  );
}
