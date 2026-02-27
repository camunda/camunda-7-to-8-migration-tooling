/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.invoice.service;

import java.util.logging.Logger;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.value.FileValue;

/**
 * <p>This is an empty service implementation illustrating how to use a plain
 * Java Class as a BPMN 2.0 Service Task delegate.</p>
 */
public class ArchiveInvoiceService implements JavaDelegate {

  private final Logger LOGGER = Logger.getLogger(ArchiveInvoiceService.class.getName());

  public void execute(DelegateExecution execution) throws Exception {

    Boolean shouldFail = (Boolean) execution.getVariable("shouldFail");
    FileValue invoiceDocumentVar  = execution.getVariableTyped("invoiceDocument");

    if(shouldFail != null && shouldFail) {
      throw new ProcessEngineException("Could not archive invoice...");
    }
    else {
      LOGGER.info("\n\n  ... Now archiving invoice "+execution.getVariable("invoiceNumber")
          +", filename: "+invoiceDocumentVar.getFilename()+" \n\n");
    }

  }

}
