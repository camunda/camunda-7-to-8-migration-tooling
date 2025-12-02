/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migrator.constants.MigratorConstants;
import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.impl.util.ConverterUtil;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import io.camunda.migrator.interceptor.VariableTypeDetector;
import io.camunda.migrator.impl.logging.VariableConverterLogs;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;

public class VariableConverter {

  @Autowired(required = false)
  protected List<VariableInterceptor> configuredVariableInterceptors;

  public VariableDbModel apply(HistoricVariableInstance historicVariable, Long processInstanceKey, Long scopeKey) {
    // Process variable through interceptors same as runtime migration
    Object convertedValue = convertValue(historicVariable);
    
    // TODO currently the VariableDbModelBuilder maps all variables to String type
    return new VariableDbModel.VariableDbModelBuilder()
        .variableKey(getNextKey())
        .name(historicVariable.getName())
        .value(convertedValue) // TODO https://github.com/camunda/camunda-bpm-platform/issues/5329
        .scopeKey(scopeKey)
        .processInstanceKey(processInstanceKey)
        .processDefinitionId(historicVariable.getProcessDefinitionKey())
        .tenantId(ConverterUtil.getTenantId(historicVariable.getTenantId()))
        .partitionId(MigratorConstants.C7_HISTORY_PARTITION_ID)
        .historyCleanupDate(convertDate(historicVariable.getRemovalTime()))
        .build();
  }

  /**
   * Converts a historic variable value using the same interceptor mechanism as runtime migration.
   * This ensures consistency between runtime and history variable transformation.
   *
   * @param historicVariable the historic variable instance to convert
   * @return the converted value
   * @throws VariableInterceptorException if any interceptor fails
   * @throws ClassCastException if historicVariable is not a VariableInstanceEntity
   */
  protected Object convertValue(HistoricVariableInstance historicVariable) {
    // In Camunda 7, HistoricVariableInstance is implemented by HistoricVariableInstanceEntity
    // which extends VariableInstanceEntity. This cast is safe for all Camunda 7.x versions.
    // The cast allows us to reuse the VariableInvocation infrastructure that expects VariableInstanceEntity.
    if (!(historicVariable instanceof VariableInstanceEntity)) {
      throw new ClassCastException(
          "Expected HistoricVariableInstance to be a VariableInstanceEntity, but got: " +
          historicVariable.getClass().getName());
    }
    VariableInstanceEntity variableEntity = (VariableInstanceEntity) historicVariable;
    
    // Use the same interceptor mechanism as runtime migration
    VariableInvocation variableInvocation = new VariableInvocation(variableEntity);
    executeInterceptors(variableInvocation);
    
    return variableInvocation.getMigrationVariable().getValue();
  }

  /**
   * Executes all configured variable interceptors on the given variable invocation.
   * Only interceptors that support the variable's type will be called.
   * This is the same logic used in VariableService for runtime migration.
   * 
   * Note: This method duplicates logic from VariableService.executeInterceptors() to avoid
   * coupling between history and runtime migration components. Both serve different purposes
   * (history vs runtime) and may diverge in future implementations.
   *
   * @param variableInvocation the variable invocation to process
   * @throws VariableInterceptorException if any interceptor fails
   */
  protected void executeInterceptors(VariableInvocation variableInvocation) {
    if (hasInterceptors()) {
      for (VariableInterceptor interceptor : configuredVariableInterceptors) {
        // Only execute interceptors that support this variable type using Camunda's native types
        if (VariableTypeDetector.supportsVariable(interceptor, variableInvocation)) {
          try {
            interceptor.execute(variableInvocation);
          } catch (Exception ex) {
            String interceptorName = interceptor.getClass().getSimpleName();
            String variableName = variableInvocation.getC7Variable().getName();
            VariableConverterLogs.logInterceptorWarn(interceptorName, variableName);

            if (ex instanceof VariableInterceptorException) {
              throw ex;
            } else {
              throw new VariableInterceptorException(VariableConverterLogs.formatInterceptorWarn(interceptorName, variableName), ex);
            }
          }
        }
      }
    }
  }

  /**
   * Checks if there are any configured variable interceptors.
   *
   * @return true if interceptors are configured, false otherwise
   */
  protected boolean hasInterceptors() {
    return configuredVariableInterceptors != null && !configuredVariableInterceptors.isEmpty();
  }

}
