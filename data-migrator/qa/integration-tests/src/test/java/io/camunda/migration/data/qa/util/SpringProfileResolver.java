/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfilesResolver;

/**
 * This class will append the required profile with the profiles already set in spring.profiles.active
 */
public final class SpringProfileResolver implements ActiveProfilesResolver {

  @Override
  public String @NotNull [] resolve(@NotNull Class<?> testClass) {
    var activeProfiles = getActiveProfiles();
    var annotatedProfiles = AnnotatedElementUtils.findAllMergedAnnotations(testClass, WithSpringProfile.class).stream().map(WithSpringProfile::value).collect(Collectors.toSet());
    return Stream.concat(activeProfiles.stream(), annotatedProfiles.stream()).distinct().toArray(String[]::new);
  }

  public static @NotNull List<String> getActiveProfiles() {
    return Arrays.stream(System.getProperty("spring.profiles.active", "")
        .split("\\s*,\\s*"))
        .filter(s -> !s.isBlank())
        .toList();
  }
}