/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for parsing skipped entities from migrator log output.
 * Provides common functionality for test classes that need to extract and validate
 * skipped entity information from captured console output.
 */
public class SkippedEntitiesLogParserUtils {

    /**
     * Parses the migrator output and extracts skipped entities.
     * Filters out debug/info logs that may appear in CI environments.
     *
     * @param output the captured console output from the migrator
     * @return a map of entity type display names to lists of entity IDs
     */
    public static Map<String, List<String>> parseSkippedEntitiesOutput(String output) {
        Map<String, List<String>> result = new HashMap<>();

        String relevantOutput = extractRelevantOutput(output);
        if (relevantOutput.isEmpty()) {
            return result;
        }

        List<EntitySection> sections = extractEntitySections(relevantOutput);

        for (EntitySection section : sections) {
            List<String> entityIds = extractEntityIds(section.content);
            result.put(section.entityType, entityIds);
        }

        return result;
    }

    protected static String extractRelevantOutput(String output) {
        Pattern startPattern = Pattern.compile("(Previously skipped \\[[^\\]]+\\]:|No entities of type \\[[^\\]]+\\] were skipped during previous migration)");
        Matcher matcher = startPattern.matcher(output);
        return matcher.find() ? output.substring(matcher.start()) : "";
    }

    protected static List<EntitySection> extractEntitySections(String output) {
        List<EntitySection> sections = new ArrayList<>();
        Pattern headerPattern = Pattern.compile("(Previously skipped \\[([^\\]]+)\\]:|No entities of type \\[([^\\]]+)\\] were skipped during previous migration)");
        Matcher matcher = headerPattern.matcher(output);

        int lastEnd = 0;
        String lastEntityType = null;

        while (matcher.find()) {
            // Process previous section if exists
            if (lastEntityType != null) {
                String content = output.substring(lastEnd, matcher.start()).trim();
                sections.add(new EntitySection(lastEntityType, content));
            }

            // Extract entity type from current match and remove trailing 's' to match enum display names
            String entityType = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            // Both "Previously skipped" and "No entities" formats use plural names, so remove trailing 's' for both
            lastEntityType = entityType.endsWith("s") ?
                entityType.substring(0, entityType.length() - 1) : entityType;
            lastEnd = matcher.end();
        }

        // Process last section
        if (lastEntityType != null) {
            String content = output.substring(lastEnd).trim();
            sections.add(new EntitySection(lastEntityType, content));
        }

        return sections;
    }

    protected static List<String> extractEntityIds(String sectionContent) {
        if (sectionContent.isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(sectionContent.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !isLogLine(line))
            .collect(Collectors.toList());
    }

    /**
     * Checks if a line is a log message that should be filtered out in CI environments.
     * This is necessary because CI may output debug/info logs mixed with actual entity IDs.
     */
    protected static boolean isLogLine(String line) {
        return line.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z.*") || // ISO timestamp
               line.matches(".*(DEBUG|INFO|WARN|ERROR).*") || // Log levels
               line.matches(".*(==>|<==|Preparing:|Parameters:|Total:).*"); // SQL logging patterns
    }

    /**
     * Internal class to hold entity section information during parsing.
     */
    protected static class EntitySection {
        final String entityType;
        final String content;

        EntitySection(String entityType, String content) {
            this.entityType = entityType;
            this.content = content;
        }
    }
}
