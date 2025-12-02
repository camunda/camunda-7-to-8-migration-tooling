/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.config.property.MigratorProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

class JobTypeConfigurationTest {

  @Nested
  @SpringBootTest
  class DefaultJobTypeWithoutConfigTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHaveDefaultJobTypeWhenNotConfigured() {
      assertThat(migratorProperties.getJobType()).isEqualTo(MigratorProperties.DEFAULT_JOB_TYPE);
      assertThat(migratorProperties.getJobType()).isEqualTo("migrator");
      assertThat(migratorProperties.getValidationJobType()).isNull();
    }

    @Test
    public void shouldFallbackToJobTypeForValidation() {
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("migrator");
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("migrator");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.job-type=custom-job-type"
  })
  class CustomJobTypeTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHaveCustomJobType() {
      assertThat(migratorProperties.getJobType()).isEqualTo("custom-job-type");
      assertThat(migratorProperties.getValidationJobType()).isNull();
    }

    @Test
    public void shouldUseJobTypeForBothValidationAndActivation() {
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("custom-job-type");
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("custom-job-type");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.validation-job-type=validation-type"
  })
  class ValidationJobTypeOnlyTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHaveValidationJobTypeWithDefaultJobType() {
      assertThat(migratorProperties.getJobType()).isEqualTo("migrator"); // default
      assertThat(migratorProperties.getValidationJobType()).isEqualTo("validation-type");
    }

    @Test
    public void shouldUseValidationJobTypeForValidationAndDefaultForActivation() {
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("validation-type");
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("migrator");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.job-type=activation-type",
      "camunda.migrator.validation-job-type=validation-type"
  })
  class SeparateJobTypesTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHaveSeparateJobTypes() {
      assertThat(migratorProperties.getJobType()).isEqualTo("activation-type");
      assertThat(migratorProperties.getValidationJobType()).isEqualTo("validation-type");
    }

    @Test
    public void shouldUseSeparateJobTypesForValidationAndActivation() {
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("validation-type");
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("activation-type");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.job-type=migrator",
      "camunda.migrator.validation-job-type==if legacyId != null then \"migrator\" else \"noop\""
  })
  class FeelExpressionValidationTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHaveFeelExpressionForValidation() {
      assertThat(migratorProperties.getJobType()).isEqualTo("migrator");
      assertThat(migratorProperties.getValidationJobType()).isEqualTo("=if legacyId != null then \"migrator\" else \"noop\"");
    }

    @Test
    public void shouldUseFeelExpressionForValidationAndStaticForActivation() {
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("=if legacyId != null then \"migrator\" else \"noop\"");
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("migrator");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.job-type=exec-migrator",
      "camunda.migrator.validation-job-type==if environment = \"production\" then \"prod-migrator\" else \"dev-migrator\""
  })
  class ComplexFeelExpressionTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHandleComplexFeelExpression() {
      assertThat(migratorProperties.getJobType()).isEqualTo("exec-migrator");
      assertThat(migratorProperties.getValidationJobType()).isEqualTo("=if environment = \"production\" then \"prod-migrator\" else \"dev-migrator\"");
    }

    @Test
    public void shouldSeparateComplexValidationFromSimpleActivation() {
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("=if environment = \"production\" then \"prod-migrator\" else \"dev-migrator\"");
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("exec-migrator");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.job-type=migrator",
      "camunda.migrator.validation-job-type=DISABLED"
  })
  class SkipValidationTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldHaveSkipValidationSet() {
      assertThat(migratorProperties.getJobType()).isEqualTo("migrator");
      assertThat(migratorProperties.getValidationJobType()).isEqualTo("DISABLED");
    }

    @Test
    public void shouldDetectValidationDisabled() {
      assertThat(migratorProperties.isJobTypeValidationDisabled()).isTrue();
      assertThat(migratorProperties.getJobActivationType()).isEqualTo("migrator");
    }

    @Test
    public void shouldStillReturnEffectiveValidationJobType() {
      // Even when disabled, the effective validation job type should return the DISABLED value
      assertThat(migratorProperties.getEffectiveValidationJobType()).isEqualTo("DISABLED");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.validation-job-type=normal-validation"
  })
  class ValidationNotDisabledTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Test
    public void shouldNotDetectValidationDisabled() {
      assertThat(migratorProperties.isJobTypeValidationDisabled()).isFalse();
      assertThat(migratorProperties.getValidationJobType()).isEqualTo("normal-validation");
    }
  }
}
