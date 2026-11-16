/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.util;

import java.util.List;
import java.util.StringJoiner;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.db.sql.MybatisJoinHelper;

public class MyBatisUtils {

  /**
   * Builds the order expression used by Camunda 7's MyBatis mappings.
   *
   * <p>MyBatis 3.6.0 scopes bind variables created inside a foreach node, so the
   * original Camunda 7 mapping cannot accumulate multiple ordering properties.
   */
  public static String orderBy(List<QueryOrderingProperty> orderingProperties) {
    if (orderingProperties == null || orderingProperties.isEmpty()) {
      return "RES.ID_ asc";
    }

    var orderBy = new StringJoiner(", ");
    for (var index = 0; index < orderingProperties.size(); index++) {
      orderBy.add(MybatisJoinHelper.orderBy(orderingProperties.get(index), index));
    }
    return orderBy.toString();
  }
}
