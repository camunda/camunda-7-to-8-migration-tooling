/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.app;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.impl.AutoDeployer;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MigratorApp {

  protected static final Logger LOGGER = LoggerFactory.getLogger(MigratorApp.class);

  protected static final int MAX_FLAGS = 5;

  protected static final String ARG_HELP = "help";
  protected static final String ARG_HISTORY_MIGRATION = "history";
  protected static final String ARG_RUNTIME_MIGRATION = "runtime";
  protected static final String ARG_IDENTITY_MIGRATION = "identity";
  protected static final String ARG_RETRY_SKIPPED = "retry-skipped";
  protected static final String ARG_LIST_SKIPPED = "list-skipped";
  protected static final String ARG_DROP_SCHEMA = "drop-schema";
  protected static final String ARG_FORCE = "force";

  protected static final Set<String> VALID_FLAGS = Set.of(
      "--" + ARG_RUNTIME_MIGRATION,
      "--" + ARG_HISTORY_MIGRATION,
      "--" + ARG_IDENTITY_MIGRATION,
      "--" + ARG_LIST_SKIPPED,
      "--" + ARG_RETRY_SKIPPED,
      "--" + ARG_DROP_SCHEMA,
      "--" + ARG_FORCE,
      "--" + ARG_HELP
  );

  protected static final Set<String> VALID_ENTITY_TYPES = IdKeyMapper.getHistoryTypeNames();
  private static List<String> argsList;

  public static void main(String[] args) {
    // Handle --help early to avoid loading Spring Boot (only when used alone)
    argsList = java.util.Arrays.asList(args);
    if (hasArg(ARG_HELP) && argsList.size() == 1) {
      printUsage();
      System.exit(1);
    }
    
    try {
      // Early validation before Spring Boot starts
      validateArguments(args);
    } catch (IllegalArgumentException e) {
      LOGGER.error("Error: {}", e.getMessage());
      printUsage();
      System.exit(1);
    }
    
    LOGGER.info("Starting migration with flags: {}", String.join(" ", args));

    // Continue with Spring Boot application
    ConfigurableApplicationContext context = new SpringApplicationBuilder(MigratorApp.class).run(args);
    ApplicationArguments appArgs = new DefaultApplicationArguments(args);
    MigratorMode mode = getMigratorMode(appArgs);
    try {
      AutoDeployer autoDeployer = context.getBean(AutoDeployer.class);
      autoDeployer.deploy();
      if (hasMigrationFlags(appArgs)) {
        runMigratorsInOrder(context, appArgs, mode, args);
      } else {
        LOGGER.error("Error: Invalid argument combination."); // Last resort error, should be caught by validation
        printUsage();
      }
    } finally {
      SpringApplication.exit(context);
    }
  }

  protected static void validateArguments(String[] args) {
    List<String> argsList = java.util.Arrays.asList(args);

    if (hasArg(ARG_HELP)) {
      if (argsList.size() > 1) {
        throw new IllegalArgumentException("The --help flag cannot be combined with other flags.");
      } else {
        return;
      }
    }

    if (!hasArg(ARG_HISTORY_MIGRATION) && !hasArg(ARG_RUNTIME_MIGRATION) && !hasArg(ARG_IDENTITY_MIGRATION)) {
      throw new IllegalArgumentException("Must specify at least one migration type: use --runtime, --history, --identity or a combination of them.");
    }

    boolean hasListSkipped = hasArg(ARG_LIST_SKIPPED);
    boolean hasRetrySkipped = hasArg(ARG_RETRY_SKIPPED);

    if (hasListSkipped && hasRetrySkipped) {
      throw new IllegalArgumentException(
          "Conflicting flags: --list-skipped and --retry-skipped cannot be used together. Please specify only one of them.");
    }

    boolean dropSchema = hasArg(ARG_DROP_SCHEMA);
    boolean force = hasArg(ARG_FORCE);
    LOGGER.debug("Migration will be run with `drop-schema={}` and `force={}`", dropSchema, force);
    if (force && !dropSchema) {
      throw new IllegalArgumentException(
          "Invalid flag combination: --force requires --drop-schema. Use both flags together or remove --force.");
    }

    boolean listSkippedHistoryFound = hasListSkipped && hasArg(ARG_HISTORY_MIGRATION);
    int flagCount = 0;

    for (String arg : args) {
      if (VALID_FLAGS.contains(arg)) {
        flagCount++;
      } else if (listSkippedHistoryFound && VALID_ENTITY_TYPES.contains(arg)) {
        // Valid entity type parameter following --list-skipped with --history
        continue;
      } else {
        throw new IllegalArgumentException("Invalid flag: " + arg);
      }
    }

    // Check if we have too many flags (not counting entity type parameters)
    if (flagCount > MAX_FLAGS) {
      throw new IllegalArgumentException("Error: Too many arguments.");
    }
  }

  protected static boolean hasArg(String arg) {
    return argsList.contains("--" + arg);
  }

  protected static void printUsage() {
    System.out.println();
    System.out.println("Usage: start.sh/bat [--help] [--runtime] [--history] [--identity] [--list-skipped [ENTITY_TYPES...]|--retry-skipped] [--drop-schema|--drop-schema --force]");
    System.out.println("Options:");
    System.out.println("  --help              - Show this help message");
    System.out.println("  --runtime           - Migrate runtime data only");
    System.out.println("  --history           - Migrate history data only. This option is still EXPERIMENTAL and not meant for production use.");
    System.out.println("  --identity          - Migrate identity data only. This option is still EXPERIMENTAL and not meant for production use.");
    System.out.println("  --runtime --history --identity - Migrate all data in the specified order");
    System.out.println("  --list-skipped [ENTITY_TYPES...]");
    System.out.println("                    - List previously skipped migration data. For history data, optionally specify entity types to filter.");
    System.out.println("                      Filter only applicable with history migration. Available entity types:");
    System.out.println("                      HISTORY_PROCESS_DEFINITION, HISTORY_PROCESS_INSTANCE, HISTORY_INCIDENT,");
    System.out.println("                      HISTORY_VARIABLE, HISTORY_USER_TASK, HISTORY_FLOW_NODE,");
    System.out.println("                      HISTORY_DECISION_INSTANCE, HISTORY_DECISION_DEFINITION");
    System.out.println("  --retry-skipped   - Retry only previously skipped history data");
    System.out.println("  --drop-schema     - Drop the migrator schema on shutdown if migration was successful");
    System.out.println("  --force           - Force the dropping of the migrator schema in all cases, to be used in combination with --drop-schema");
    System.out.println();
    System.out.println("Mutually exclusive options:");
    System.out.println("  --list-skipped and --retry-skipped cannot be used together");
    System.out.println("  --force can only be used with --drop-schema");
    System.out.println("  --help cannot be used with any other flag");
    System.out.println("Examples:");
    System.out.println("  start.sh --history --list-skipped");
    System.out.println("  start.sh --history --list-skipped HISTORY_PROCESS_INSTANCE HISTORY_USER_TASK");
  }

  protected static boolean hasMigrationFlags(ApplicationArguments appArgs) {
    return appArgs.containsOption(ARG_RUNTIME_MIGRATION) || appArgs.containsOption(ARG_HISTORY_MIGRATION) || appArgs.containsOption(ARG_IDENTITY_MIGRATION);
  }

  protected static void runMigratorsInOrder(ConfigurableApplicationContext context, ApplicationArguments appArgs, MigratorMode mode, String[] args) {
    boolean runtimeAlreadyProcessed = false;
    boolean historyAlreadyProcessed = false;
    boolean identityAlreadyProcessed = false;

    // Run migrators in the order they appear in command line arguments
    for (String arg : args) {
      if (("--" + ARG_RUNTIME_MIGRATION).equals(arg) && !runtimeAlreadyProcessed) {
        migrateRuntime(context, mode);
        runtimeAlreadyProcessed = true;
      } else if (("--" + ARG_HISTORY_MIGRATION).equals(arg) && !historyAlreadyProcessed) {
        migrateHistory(context, appArgs, mode);
        historyAlreadyProcessed = true;
      } else if (("--" + ARG_IDENTITY_MIGRATION).equals(arg) && !identityAlreadyProcessed) {
        migrateIdentity(context, mode);
        identityAlreadyProcessed = true;
      }
    }
  }

  public static void migrateRuntime(ConfigurableApplicationContext context, MigratorMode mode) {
    LOGGER.info("Migrating runtime data...");
    RuntimeMigrator runtimeMigrator = context.getBean(RuntimeMigrator.class);
    runtimeMigrator.setMode(mode);
    runtimeMigrator.start();
  }

  public static void migrateHistory(ConfigurableApplicationContext context, ApplicationArguments appArgs, MigratorMode mode) {
    LOGGER.info("Migrating history data...");
    HistoryMigrator historyMigrator = null;
    try {
      historyMigrator = context.getBean(HistoryMigrator.class);
    } catch (NoSuchBeanDefinitionException ex) {
      LOGGER.error("No C8 datasource configured. Configure 'camunda.migrator.c8.datasource' to allow history migration.");
      System.exit(1);
    }

    historyMigrator.setMode(mode);

    // Extract entity type filters if --list-skipped is used
    if (mode == MigratorMode.LIST_SKIPPED) {
      List<IdKeyMapper.TYPE> entityTypeFilters = extractEntityTypeFilters(appArgs);
      if (!entityTypeFilters.isEmpty()) {
        historyMigrator.setRequestedEntityTypes(entityTypeFilters);
      }
    }

    historyMigrator.start();
  }

  public static void migrateIdentity(ConfigurableApplicationContext context, MigratorMode mode) {
    LOGGER.info("Migrating identity data...");
    IdentityMigrator identityMigrator = context.getBean(IdentityMigrator.class);
    if (mode != MigratorMode.MIGRATE) { // Retry covered by https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/440
      throw new UnsupportedOperationException("Only migrate mode is supported for identity data");
    }
    identityMigrator.migrate();
  }

  protected static List<IdKeyMapper.TYPE> extractEntityTypeFilters(ApplicationArguments appArgs) {
    List<String> nonOptionArgs = appArgs.getNonOptionArgs();
    return nonOptionArgs.stream()
        .filter(VALID_ENTITY_TYPES::contains)
        .map(IdKeyMapper.TYPE::valueOf)
        .toList();
  }

  protected static MigratorMode getMigratorMode(ApplicationArguments appArgs) {
    if (appArgs.containsOption(ARG_LIST_SKIPPED)) {
      return MigratorMode.LIST_SKIPPED;
    } else if (appArgs.containsOption(ARG_RETRY_SKIPPED)) {
      return MigratorMode.RETRY_SKIPPED;
    } else {
      return MigratorMode.MIGRATE;
    }
  }
}
