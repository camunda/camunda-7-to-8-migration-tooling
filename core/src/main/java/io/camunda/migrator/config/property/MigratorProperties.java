/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config.property;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the migrator.
 */
@ConfigurationProperties(MigratorProperties.PREFIX)
public class MigratorProperties {

  public static final int DEFAULT_PAGE_SIZE = 100;
  public static final int DEFAULT_BATCH_SIZE = 100;
  public static final String PREFIX = "camunda.migrator";
  public static final String DEFAULT_JOB_TYPE = "migrator";

  public enum DataSource {
    C7, C8
  }

  protected Integer pageSize = DEFAULT_PAGE_SIZE;
  protected Integer batchSize = DEFAULT_BATCH_SIZE;
  protected DataSource dataSource = DataSource.C7;
  protected String jobType = DEFAULT_JOB_TYPE;
  protected String validationJobType;
  protected Set<String> tenantIds;
  protected boolean saveSkipReason = false;

  protected Boolean autoDdl;
  protected String tablePrefix;

  protected C7Properties c7;
  protected C8Properties c8;

  protected List<InterceptorProperty> interceptors;

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
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

  public DataSource getDataSource() {
    return dataSource;
  }

  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
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

  public List<InterceptorProperty> getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(List<InterceptorProperty> interceptors) {
    this.interceptors = interceptors;
  }
}
