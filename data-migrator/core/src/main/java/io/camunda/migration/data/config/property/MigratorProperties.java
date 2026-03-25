/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property;

import io.camunda.migration.data.config.property.history.HistoryProperties;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the migrator.
 */
@Validated
@ConfigurationProperties(MigratorProperties.PREFIX)
public class MigratorProperties {

  public static final int DEFAULT_PAGE_SIZE = 100;
  public static final String PREFIX = "camunda.migrator";
  public static final String DEFAULT_JOB_TYPE = "migrator";

  protected Integer pageSize = DEFAULT_PAGE_SIZE;
  protected String jobType = DEFAULT_JOB_TYPE;
  protected String validationJobType;
  protected Set<String> tenantIds;
  protected boolean saveSkipReason = false;

  protected Boolean autoDdl;
  protected String tablePrefix;

  protected C7Properties c7;
  protected C8Properties c8;
  protected HistoryProperties history;
  protected IdentityProperties identity = new IdentityProperties();

  protected List<InterceptorConfig> interceptors;

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public C7Properties getC7() {
    return c7;
  }

  public void setC7(C7Properties c7) {
    this.c7 = c7;
  }

  public C8Properties getC8() {
    return c8;
  }

  public void setC8(C8Properties c8) {
    this.c8 = c8;
  }

  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(IdentityProperties identity) {
    this.identity = identity;
  }

  public Boolean getAutoDdl() {
    return autoDdl;
  }

  public void setAutoDdl(Boolean autoDdl) {
    this.autoDdl = autoDdl;
  }

  public String getTablePrefix() {
    return tablePrefix;
  }

  public void setTablePrefix(String tablePrefix) {
    this.tablePrefix = tablePrefix;
  }


  public String getJobType() {
    return jobType;
  }

  public void setJobType(String jobType) {
    this.jobType = jobType;
  }

  public String getValidationJobType() {
    return validationJobType;
  }

  public void setValidationJobType(String validationJobType) {
    this.validationJobType = validationJobType;
  }

  public Set<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(Set<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public boolean getSaveSkipReason() {
    return saveSkipReason;
  }

  public void setSaveSkipReason(boolean saveSkipReason) {
    this.saveSkipReason = saveSkipReason;
  }

  /**
   * Returns the job type to use for validation purposes.
   * If validation-job-type is defined, it returns that value.
   * Otherwise, it returns the job-type value.
   */
  public String getEffectiveValidationJobType() {
    return validationJobType != null ? validationJobType : jobType;
  }

  /**
   * Returns whether job type validation should be skipped entirely.
   * Returns true if validation-job-type is set to "DISABLED".
   */
  public boolean isJobTypeValidationDisabled() {
    return "DISABLED".equals(validationJobType);
  }

  /**
   * Returns the job type to use for job activation in activateMigratorJobs.
   * This always returns the job-type value.
   */
  public String getJobActivationType() {
    return jobType;
  }

  public List<InterceptorConfig> getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(List<InterceptorConfig> interceptors) {
    this.interceptors = interceptors;
  }

  public HistoryProperties getHistory() {
    return history;
  }

  public void setHistory(HistoryProperties history) {
    this.history = history;
  }

  @AssertTrue(message = "When skip-groups is enabled, skip-users must also be enabled")
  public boolean isCombinationValid() {
    if (identity.getSkipGroups() == true && identity.getSkipUsers() == false) {
      return false;
    }
    return true;
  }
}
