/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.mybatis;

import static java.util.stream.Collectors.joining;

import java.util.stream.IntStream;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.db.sql.MybatisJoinHelper;

/**
 * Restores Camunda 7's generated {@code internalOrderBy} value when MyBatis 3.6 evaluates mapper
 * bindings outside their foreach context.
 */
public final class Camunda7QueryObjectWrapperFactory implements ObjectWrapperFactory {

  protected static final String DEFAULT_ORDER_BY = "RES.ID_ asc";
  protected static final String INTERNAL_ORDER_BY = "internalOrderBy";

  protected final ObjectWrapperFactory delegate;

  public Camunda7QueryObjectWrapperFactory(final ObjectWrapperFactory delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean hasWrapperFor(final Object object) {
    return object instanceof ListQueryParameterObject || delegate.hasWrapperFor(object);
  }

  @Override
  public ObjectWrapper getWrapperFor(final MetaObject metaObject, final Object object) {
    if (object instanceof ListQueryParameterObject query) {
      return new Camunda7QueryObjectWrapper(metaObject, query);
    }
    return delegate.getWrapperFor(metaObject, object);
  }

  protected static final class Camunda7QueryObjectWrapper extends BeanWrapper {

    protected final ListQueryParameterObject query;

    public Camunda7QueryObjectWrapper(
        final MetaObject metaObject, final ListQueryParameterObject query) {
      super(metaObject, query);
      this.query = query;
    }

    @Override
    public Object get(final PropertyTokenizer propertyTokenizer) {
      if (INTERNAL_ORDER_BY.equals(propertyTokenizer.getName())
          && !propertyTokenizer.hasNext()) {
        return internalOrderBy();
      }
      return super.get(propertyTokenizer);
    }

    protected String internalOrderBy() {
      var orderingProperties = query.getOrderingProperties();
      if (orderingProperties == null || orderingProperties.isEmpty()) {
        return DEFAULT_ORDER_BY;
      }
      return IntStream.range(0, orderingProperties.size())
          .mapToObj(index -> MybatisJoinHelper.orderBy(orderingProperties.get(index), index))
          .collect(joining(", "));
    }
  }
}
