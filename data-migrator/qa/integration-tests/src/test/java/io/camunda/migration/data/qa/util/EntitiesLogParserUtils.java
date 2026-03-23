/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for parsing entity information from migrator log output.
 * Provides common functionality for test classes that need to extract and validate
 * migrated or skipped entity information from captured console output.
 */
public class EntitiesLogParserUtils {

    private static final Pattern MIGRATED_START_PATTERN = Pattern.compile(
        "(Migration mappings for \\[[^\\]]+\\]:|No entities of type \\[[^\\]]+\\] were migrated)");
    private static final Pattern MIGRATED_HEADER_PATTERN = Pattern.compile(
        "(Migration mappings for \\[([^\\]]+)\\]:|No entities of type \\[([^\\]]+)\\] were migrated)");

    private static final Pattern SKIPPED_START_PATTERN = Pattern.compile(
        "(Previously skipped \\[[^\\]]+\\]:|No entities of type \\[[^\\]]+\\] were skipped during previous migration)");
    private static final Pattern SKIPPED_HEADER_PATTERN = Pattern.compile(
        "(Previously skipped \\[([^\\]]+)\\]:|No entities of type \\[([^\\]]+)\\] were skipped during previous migration)");

    /**
     * Parses the migrator output and extracts migrated entity mappings.
     *
     * @param output the captured console output from the migrator
     * @return a map of entity type display names to lists of mapping strings ("c7Id c8Key")
     */
    public static Map<String, List<String>> parseMigratedEntitiesOutput(String output) {
        return parseOutput(output, MIGRATED_START_PATTERN, MIGRATED_HEADER_PATTERN);
    }

    /**
     * Parses the migrator output and extracts skipped entities.
     *
     * @param output the captured console output from the migrator
     * @return a map of entity type display names to lists of entity IDs
     */
    public static Map<String, List<String>> parseSkippedEntitiesOutput(String output) {
        return parseOutput(output, SKIPPED_START_PATTERN, SKIPPED_HEADER_PATTERN);
    }

    private static Map<String, List<String>> parseOutput(String output, Pattern startPattern, Pattern headerPattern) {
        Map<String, List<String>> result = new HashMap<>();

        String relevantOutput = extractRelevantOutput(output, startPattern);
        if (relevantOutput.isEmpty()) {
            return result;
        }

        List<EntitySection> sections = extractEntitySections(relevantOutput, headerPattern);

        for (EntitySection section : sections) {
            List<String> entries = extractEntries(section.content);
            result.put(section.entityType, entries);
        }

        return result;
    }

    private static String extractRelevantOutput(String output, Pattern startPattern) {
        Matcher matcher = startPattern.matcher(output);
        return matcher.find() ? output.substring(matcher.start()) : "";
    }

    private static List<EntitySection> extractEntitySections(String output, Pattern headerPattern) {
        List<EntitySection> sections = new ArrayList<>();
        Matcher matcher = headerPattern.matcher(output);

        int lastEnd = 0;
        String lastEntityType = null;

        while (matcher.find()) {
            if (lastEntityType != null) {
                String content = output.substring(lastEnd, matcher.start()).trim();
                sections.add(new EntitySection(lastEntityType, content));
            }

            String entityType = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            // Header formats use plural names, so remove trailing 's'
            lastEntityType = entityType.endsWith("s") ?
                entityType.substring(0, entityType.length() - 1) : entityType;
            lastEnd = matcher.end();
        }

        if (lastEntityType != null) {
            String content = output.substring(lastEnd).trim();
            sections.add(new EntitySection(lastEntityType, content));
        }

        return sections;
    }

    private static List<String> extractEntries(String sectionContent) {
        if (sectionContent.isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(sectionContent.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !isLogLine(line) && !isColumnHeader(line))
            .collect(Collectors.toList());
    }

    /**
     * Checks if a line is a column header that should be filtered out from entity entries.
     */
    private static boolean isColumnHeader(String line) {
        return "Camunda7-Id Camunda8-Key".equals(line);
    }

    /**
     * Checks if a line is a log message that should be filtered out in CI environments.
     */
    private static boolean isLogLine(String line) {
        return line.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z.*") || // ISO timestamp
               line.matches(".*(DEBUG|INFO|WARN|ERROR).*") || // Log levels
               line.matches(".*(==>|<==|Preparing:|Parameters:|Total:).*"); // SQL logging patterns
    }

    private static class EntitySection {
        final String entityType;
        final String content;

        EntitySection(String entityType, String content) {
            this.entityType = entityType;
            this.content = content;
        }
    }
}
