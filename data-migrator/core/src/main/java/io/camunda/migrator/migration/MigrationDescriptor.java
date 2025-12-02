/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.migration;

import io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Describes how to migrate a specific entity type from Camunda 7 to Camunda 8.
 * This encapsulates all the configuration needed to execute a migration for one entity type.
 *
 * @param <C7Entity> The Camunda 7 entity type (e.g., HistoricProcessInstance)
 */
public class MigrationDescriptor<C7Entity> {

  private final TYPE type;
  private final Function<String, C7Entity> c7Fetcher;
  private final BiConsumer<Consumer<C7Entity>, Date> c7BatchFetcher;
  private final Consumer<C7Entity> migrator;
  private final Runnable startLogger;

  private MigrationDescriptor(Builder<C7Entity> builder) {
    this.type = builder.type;
    this.c7Fetcher = builder.c7Fetcher;
    this.c7BatchFetcher = builder.c7BatchFetcher;
    this.migrator = builder.migrator;
    this.startLogger = builder.startLogger;
  }

  public TYPE type() {
    return type;
  }

  public Function<String, C7Entity> c7Fetcher() {
    return c7Fetcher;
  }

  public BiConsumer<Consumer<C7Entity>, Date> c7BatchFetcher() {
    return c7BatchFetcher;
  }

  public Consumer<C7Entity> migrator() {
    return migrator;
  }

  public void logStart() {
    if (startLogger != null) {
      startLogger.run();
    }
  }

  public static <C7Entity> Builder<C7Entity> builder() {
    return new Builder<>();
  }

  public static class Builder<C7Entity> {
    private TYPE type;
    private Function<String, C7Entity> c7Fetcher;
    private BiConsumer<Consumer<C7Entity>, Date> c7BatchFetcher;
    private Consumer<C7Entity> migrator;
    private Runnable startLogger;

    public Builder<C7Entity> type(TYPE type) {
      this.type = type;
      return this;
    }

    public Builder<C7Entity> c7Fetcher(Function<String, C7Entity> c7Fetcher) {
      this.c7Fetcher = c7Fetcher;
      return this;
    }

    public Builder<C7Entity> c7BatchFetcher(BiConsumer<Consumer<C7Entity>, Date> c7BatchFetcher) {
      this.c7BatchFetcher = c7BatchFetcher;
      return this;
    }

    public Builder<C7Entity> migrator(Consumer<C7Entity> migrator) {
      this.migrator = migrator;
      return this;
    }

    public Builder<C7Entity> startLogger(Runnable startLogger) {
      this.startLogger = startLogger;
      return this;
    }

    public MigrationDescriptor<C7Entity> build() {
      if (type == null || c7Fetcher == null || c7BatchFetcher == null || migrator == null) {
        throw new IllegalStateException("type, c7Fetcher, c7BatchFetcher, and migrator are required");
      }
      return new MigrationDescriptor<>(this);
    }
  }
}

