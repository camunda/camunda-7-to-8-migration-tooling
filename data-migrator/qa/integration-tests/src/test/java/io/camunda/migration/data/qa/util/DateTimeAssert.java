/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static java.time.ZoneId.systemDefault;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertion for comparing dates and times in migration tests.
 * This class handles the conversion of different date/time types to a common format
 * (ZonedDateTime in system default timezone) for easier comparison.
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Compare OffsetDateTime with Date
 * assertThatDateTime(userTask.completionDate())
 *     .isEqualToLocalTime(taskCompletionTime);
 *
 * // Compare OffsetDateTime with another OffsetDateTime
 * assertThatDateTime(userTask.completionDate())
 *     .isEqualToLocalTime(processInstanceEndDate);
 *
 * // Compare with null
 * assertThatDateTime(userTask.completionDate())
 *     .isNotNull();
 * </pre>
 */
public class DateTimeAssert extends AbstractAssert<DateTimeAssert, OffsetDateTime> {

  protected DateTimeAssert(OffsetDateTime actual) {
    super(actual, DateTimeAssert.class);
  }

  /**
   * Creates a new DateTimeAssert instance for the given OffsetDateTime.
   *
   * @param actual the actual value
   * @return the created assertion object
   */
  public static DateTimeAssert assertThatDateTime(OffsetDateTime actual) {
    return new DateTimeAssert(actual);
  }

  /**
   * Verifies that the actual datetime equals the expected datetime when both are converted
   * to the system default timezone. This handles timezone differences and makes comparisons
   * more intuitive.
   *
   * @param expected the expected Date (typically from Camunda 7)
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isEqualToLocalTime(Date expected) {
    isNotNull();
    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = makeLocalTime(convertDate(expected));

    if (!actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime to be equal to <%s> (in local time) but was <%s>",
          expectedLocal, actualLocal
      );
    }
    return this;
  }

  /**
   * Verifies that the actual datetime equals the expected datetime when both are converted
   * to the system default timezone. This handles timezone differences and makes comparisons
   * more intuitive.
   *
   * @param expected the expected OffsetDateTime (typically from Camunda 8)
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isEqualToLocalTime(OffsetDateTime expected) {
    isNotNull();
    if (expected == null) {
      failWithMessage("Expected datetime should not be null");
      return this;
    }

    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = makeLocalTime(expected);

    if (!actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime to be equal to <%s> (in local time) but was <%s>",
          expectedLocal, actualLocal
      );
    }
    return this;
  }

  /**
   * Verifies that the actual datetime equals the expected ZonedDateTime when both are converted
   * to the system default timezone. This handles timezone differences and makes comparisons
   * more intuitive.
   *
   * @param expected the expected ZonedDateTime
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isEqualToLocalTime(ZonedDateTime expected) {
    isNotNull();
    if (expected == null) {
      failWithMessage("Expected datetime should not be null");
      return this;
    }

    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = expected.withZoneSameInstant(systemDefault());

    if (!actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime to be equal to <%s> (in local time) but was <%s>",
          expectedLocal, actualLocal
      );
    }
    return this;
  }

  /**
   * Verifies that the actual datetime equals the base datetime plus the given amount when
   * both are converted to the system default timezone. This is useful for comparing cleanup
   * dates or other calculated timestamps.
   *
   * <p>Example:</p>
   * <pre>
   * assertThatDateTime(cleanupDate)
   *     .isEqualToLocalTimePlus(evaluationDate, Period.ofDays(180));
   * </pre>
   *
   * @param base the base OffsetDateTime to add the amount to
   * @param amount the temporal amount to add (e.g., Period.ofDays(180))
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isEqualToLocalTimePlus(OffsetDateTime base, TemporalAmount amount) {
    isNotNull();
    if (base == null) {
      failWithMessage("Base datetime should not be null");
      return this;
    }

    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = makeLocalTime(base).plus(amount);

    if (!actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime to be equal to <%s> + <%s> = <%s> (in local time) but was <%s>",
          makeLocalTime(base), amount, expectedLocal, actualLocal
      );
    }
    return this;
  }

  /**
   * Verifies that the actual datetime equals the base Date plus the given amount when
   * both are converted to the system default timezone.
   *
   * @param base the base Date to add the amount to
   * @param amount the temporal amount to add (e.g., Period.ofDays(180))
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isEqualToLocalTimePlus(Date base, TemporalAmount amount) {
    isNotNull();
    if (base == null) {
      failWithMessage("Base datetime should not be null");
      return this;
    }

    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = makeLocalTime(convertDate(base)).plus(amount);

    if (!actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime to be equal to <%s> + <%s> = <%s> (in local time) but was <%s>",
          makeLocalTime(convertDate(base)), amount, expectedLocal, actualLocal
      );
    }
    return this;
  }

  /**
   * Verifies that the actual datetime does not equal the expected datetime when both are
   * converted to the system default timezone.
   *
   * @param expected the expected Date
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isNotEqualToLocalTime(Date expected) {
    isNotNull();
    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = makeLocalTime(convertDate(expected));

    if (actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime not to be equal to <%s> (in local time) but it was",
          expectedLocal
      );
    }
    return this;
  }

  /**
   * Verifies that the actual datetime does not equal the expected datetime when both are
   * converted to the system default timezone.
   *
   * @param expected the expected OffsetDateTime
   * @return this assertion object for method chaining
   */
  public DateTimeAssert isNotEqualToLocalTime(OffsetDateTime expected) {
    isNotNull();
    if (expected == null) {
      // If expected is null and actual is not, they're different, which is what we want
      return this;
    }

    ZonedDateTime actualLocal = makeLocalTime(actual);
    ZonedDateTime expectedLocal = makeLocalTime(expected);

    if (actualLocal.isEqual(expectedLocal)) {
      failWithMessage(
          "Expected datetime not to be equal to <%s> (in local time) but it was",
          expectedLocal
      );
    }
    return this;
  }

  /**
   * Converts an OffsetDateTime to ZonedDateTime in the system default timezone.
   * This allows for consistent comparison regardless of the original timezone offset.
   *
   * @param offsetDateTime the datetime to convert
   * @return the datetime in system default timezone
   */
  private ZonedDateTime makeLocalTime(OffsetDateTime offsetDateTime) {
    return offsetDateTime.atZoneSameInstant(systemDefault());
  }
}

