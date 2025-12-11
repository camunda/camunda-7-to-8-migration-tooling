/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.conversion;

import java.util.List;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.community.migration.converter.DiagramCheckResult.ElementCheckMessage;
import org.camunda.community.migration.converter.convertible.Convertible;

public interface Conversion {
  void convert(DomElement element, Convertible convertible, List<ElementCheckMessage> messages);
}
