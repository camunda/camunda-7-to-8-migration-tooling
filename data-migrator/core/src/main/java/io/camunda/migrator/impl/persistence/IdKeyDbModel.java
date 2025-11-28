/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.persistence;

import io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import java.util.Date;
import java.util.Objects;

public class IdKeyDbModel {

  protected String c7Id;
  protected Long c8Key;
  protected TYPE type;
  protected Date createTime;
  protected String skipReason;

  public IdKeyDbModel() {
  }

  public IdKeyDbModel(String c7Id, Date createTime) {
    this.c7Id = c7Id;
    this.createTime = createTime;
  }

  public Long getC8Key() {
    return c8Key;
  }

  public String getC7Id() {
    return c7Id;
  }

  public TYPE getType() {
    return type;
  }

  public Date getCreateTime() {
    return createTime;
  }

  public String getSkipReason() {
    return skipReason;
  }

  public void setC8Key(Long c8Key) {
    this.c8Key = c8Key;
  }

  public void setC7Id(String c7Id) {
    this.c7Id = c7Id;
  }

  public void setType(TYPE type) {
    this.type = type;
  }

  public void setCreateTime(Date createTime) {
    this.createTime = createTime;
  }

  public void setSkipReason(String skipReason) {
    this.skipReason = skipReason;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (IdKeyDbModel) obj;
    return Objects.equals(this.c8Key, that.c8Key) && Objects.equals(this.c7Id, that.c7Id) && Objects.equals(this.type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(c8Key, c7Id, type);
  }

  @Override
  public String toString() {
    return "IdKey[" + "c8Key=" + c8Key + ", " + "c7Id=" + c7Id + ", " + "type=" + type + ']';
  }

}
