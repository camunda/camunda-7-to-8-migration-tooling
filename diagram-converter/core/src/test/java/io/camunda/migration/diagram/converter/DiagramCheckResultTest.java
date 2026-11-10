/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the element-id lookup index of {@link DiagramCheckResult} (see issue #3695). The index
 * is built lazily and must stay consistent with the results list under appends, replacements,
 * removals, and duplicate ids.
 */
public class DiagramCheckResultTest {

  private static ElementCheckResult result(String elementId) {
    ElementCheckResult result = new ElementCheckResult();
    result.setElementId(elementId);
    return result;
  }

  @Test
  void shouldReturnNullForUnknownOrNullId() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    checkResult.getResults().add(result("a"));

    assertThat(checkResult.getResult("missing")).isNull();
    assertThat(checkResult.getResult(null)).isNull();
  }

  @Test
  void shouldIndexResultsAppendedAfterLookup() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult first = result("a");
    checkResult.getResults().add(first);

    // populate the index
    assertThat(checkResult.getResult("a")).isSameAs(first);

    // appends after the lookup must be indexed incrementally
    ElementCheckResult second = result("b");
    checkResult.getResults().add(second);
    assertThat(checkResult.getResult("b")).isSameAs(second);
    assertThat(checkResult.getResult("a")).isSameAs(first);
  }

  @Test
  void shouldReturnFirstMatchForDuplicateIds() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult first = result("dup");
    ElementCheckResult second = result("dup");
    checkResult.getResults().add(first);
    checkResult.getResults().add(second);

    assertThat(checkResult.getResult("dup")).isSameAs(first);
  }

  @Test
  void shouldIgnoreResultsWithoutElementId() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    checkResult.getResults().add(result(null));

    assertThat(checkResult.getResult(null)).isNull();
    assertThat(checkResult.getResult("a")).isNull();
  }

  @Test
  void shouldReindexAfterClearAndAppend() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult removed = result("a");
    checkResult.getResults().add(removed);

    // populate the index
    assertThat(checkResult.getResult("a")).isSameAs(removed);

    // clear + append keeps the list size unchanged, but the index must not go stale
    checkResult.getResults().clear();
    ElementCheckResult added = result("b");
    checkResult.getResults().add(added);

    assertThat(checkResult.getResult("a")).isNull();
    assertThat(checkResult.getResult("b")).isSameAs(added);
  }

  @Test
  void shouldReindexAfterRemoval() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult removed = result("a");
    ElementCheckResult kept = result("b");
    checkResult.getResults().add(removed);
    checkResult.getResults().add(kept);

    // populate the index
    assertThat(checkResult.getResult("a")).isSameAs(removed);

    checkResult.getResults().remove(0);

    assertThat(checkResult.getResult("a")).isNull();
    assertThat(checkResult.getResult("b")).isSameAs(kept);
  }

  @Test
  void shouldReindexAfterInPlaceReplacement() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult replaced = result("a");
    checkResult.getResults().add(replaced);

    // populate the index
    assertThat(checkResult.getResult("a")).isSameAs(replaced);

    ElementCheckResult replacement = result("b");
    checkResult.getResults().set(0, replacement);

    assertThat(checkResult.getResult("a")).isNull();
    assertThat(checkResult.getResult("b")).isSameAs(replacement);
  }

  @Test
  void shouldReindexAfterSetResults() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    checkResult.getResults().add(result("a"));

    // populate the index
    assertThat(checkResult.getResult("a")).isNotNull();

    ElementCheckResult replacement = result("b");
    checkResult.setResults(new ArrayList<>(List.of(replacement)));

    assertThat(checkResult.getResult("a")).isNull();
    assertThat(checkResult.getResult("b")).isSameAs(replacement);
  }

  @Test
  void shouldReindexWhenListIsReplacedWithSameInstance() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult removed = result("a");
    checkResult.getResults().add(removed);

    // populate the index
    assertThat(checkResult.getResult("a")).isSameAs(removed);

    List<ElementCheckResult> sameInstance = checkResult.getResults();
    sameInstance.clear();
    ElementCheckResult added = result("b");
    sameInstance.add(added);
    checkResult.setResults(sameInstance);

    assertThat(checkResult.getResult("a")).isNull();
    assertThat(checkResult.getResult("b")).isSameAs(added);
  }

  @Test
  void shouldNotTrackMutationsOfListRetainedByCallerAfterSetResults() {
    DiagramCheckResult checkResult = new DiagramCheckResult();
    ElementCheckResult original = result("a");
    List<ElementCheckResult> supplied = new ArrayList<>(List.of(original));
    checkResult.setResults(supplied);

    // populate the index
    assertThat(checkResult.getResult("a")).isSameAs(original);

    // mutating the caller-retained list must not leak into the check result
    ElementCheckResult replacement = result("b");
    supplied.set(0, replacement);

    assertThat(checkResult.getResult("a")).isSameAs(original);
    assertThat(checkResult.getResult("b")).isNull();
    assertThat(checkResult.getResults()).containsExactly(original);
  }
}
