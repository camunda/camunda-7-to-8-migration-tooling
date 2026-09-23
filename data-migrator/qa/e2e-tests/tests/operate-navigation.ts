/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { Page } from '@playwright/test';

const OPERATE_LOGIN_URL = 'http://localhost:8088/operate/login';
const NAVIGATION_TIMEOUT = 60000;
const NAVIGATION_ATTEMPTS = 2;

/**
 * Navigates to Operate's login page, retrying the browser navigation once when
 * the service is temporarily unresponsive. This keeps transient service
 * startup delays inside the test setup instead of producing a retried-green
 * Playwright test, which CI correctly reports as flaky.
 */
export async function navigateToOperateLogin(page: Page) {
  let lastError: unknown;

  for (let attempt = 0; attempt < NAVIGATION_ATTEMPTS; attempt += 1) {
    try {
      const response = await page.goto(OPERATE_LOGIN_URL, {
        waitUntil: 'domcontentloaded',
        timeout: NAVIGATION_TIMEOUT,
      });

      if (!response || !response.ok()) {
        throw new Error(
          `Operate login returned HTTP ${response?.status() ?? 'no response'}`,
        );
      }

      return;
    } catch (error) {
      lastError = error;
      if (attempt < NAVIGATION_ATTEMPTS - 1) {
        await page.waitForTimeout(1000);
      }
    }
  }

  throw lastError instanceof Error
    ? lastError
    : new Error('Unable to navigate to Operate login page');
}
