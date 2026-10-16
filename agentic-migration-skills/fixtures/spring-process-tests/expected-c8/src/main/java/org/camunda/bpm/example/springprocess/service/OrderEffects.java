/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEffects {

  private final JdbcTemplate jdbcTemplate;

  public OrderEffects(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  public void createTable() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS order_effects (stage VARCHAR(32) NOT NULL)");
  }

  public void record(String stage) {
    jdbcTemplate.update("INSERT INTO order_effects (stage) VALUES (?)", stage);
  }

  public int count(String stage) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM order_effects WHERE stage = ?", Integer.class, stage);
  }

  public void clear() {
    jdbcTemplate.update("DELETE FROM order_effects");
  }
}
