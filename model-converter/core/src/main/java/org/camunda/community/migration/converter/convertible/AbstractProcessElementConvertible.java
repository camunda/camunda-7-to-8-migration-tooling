/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.convertible;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractProcessElementConvertible implements Convertible {
  private Set<ZeebeProperty> zeebeProperties;

  public Set<ZeebeProperty> getZeebeProperties() {
    return zeebeProperties;
  }

  public void setZeebeProperties(Set<ZeebeProperty> zeebeProperties) {
    this.zeebeProperties = zeebeProperties;
  }

  public void addZeebeProperty(String name, String value) {
    ZeebeProperty property = new ZeebeProperty();
    property.setName(name);
    property.setValue(value);
    if (zeebeProperties == null) {
      zeebeProperties = new HashSet<>();
    }
    zeebeProperties.add(property);
  }

  public static class ZeebeProperty {
    private String name;
    private String value;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }
}
