/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import java.security.SecureRandom;

public class SecurePasswordGenerator {

  protected static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  protected static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
  protected static final String DIGITS = "0123456789";
  protected static final String SPECIAL = "!@#$%^&*()-_=+[]{};:,.?";
  protected static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

  protected static final SecureRandom RANDOM = new SecureRandom();
  protected static final int DEFAULT_PASSWORD_LENGTH = 16;

  public static String generate() {
    return generate(DEFAULT_PASSWORD_LENGTH);
  }

  public static String generate(int length) {
    char[] pwd = new char[length];
    int idx = 0;

    // ensure at least one of each set
    pwd[idx++] = randomChar(UPPER);
    pwd[idx++] = randomChar(LOWER);
    pwd[idx++] = randomChar(DIGITS);
    pwd[idx++] = randomChar(SPECIAL);

    // fill the rest from the "all" set
    while (idx < length) {
      pwd[idx++] = randomChar(ALL);
    }

    // Fisher–Yates shuffle so order is unpredictable
    for (int i = pwd.length - 1; i > 0; i--) {
      int j = RANDOM.nextInt(i + 1);
      char tmp = pwd[i];
      pwd[i] = pwd[j];
      pwd[j] = tmp;
    }

    return new String(pwd);
  }

  protected static char randomChar(String chars) {
    return chars.charAt(RANDOM.nextInt(chars.length()));
  }
}