/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.convertible;

public class ScriptTaskConvertible extends AbstractActivityConvertible {
  private String expression;
  private String resultVariable;

  public String getExpression() {
    return expression;
  }

  public void setExpression(String expression) {
    this.expression = expression;
  }

  public String getResultVariable() {
    return resultVariable;
  }

  public void setResultVariable(String resultVariable) {
    this.resultVariable = resultVariable;
  }
}
