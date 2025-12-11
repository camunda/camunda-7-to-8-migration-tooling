/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter;

public abstract class AbstractFactory<T> {
  private T instance;

  public void setInstance(T instance) {
    this.instance = instance;
  }

  protected abstract T createInstance();

  public T get() {
    if (instance == null) {
      instance = createInstance();
    }
    return instance;
  }
}
