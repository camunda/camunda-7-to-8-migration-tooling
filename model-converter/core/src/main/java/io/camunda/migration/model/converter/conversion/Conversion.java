/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.conversion;

import io.camunda.migration.model.converter.DiagramCheckResult.ElementCheckMessage;
import io.camunda.migration.model.converter.convertible.Convertible;
import java.util.List;
import org.camunda.bpm.model.xml.instance.DomElement;

public interface Conversion {
  void convert(DomElement element, Convertible convertible, List<ElementCheckMessage> messages);
}
