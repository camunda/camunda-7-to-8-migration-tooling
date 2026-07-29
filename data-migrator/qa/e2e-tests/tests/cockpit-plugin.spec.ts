/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test, expect, Page } from '@playwright/test';

/**
 * E2E smoke test for the Cockpit plugin
 *
 * This test validates that:
 * 1. Camunda 7 starts successfully with the plugin deployed
 * 2. The Cockpit UI is accessible
 * 3. The plugin UI is visible on the processes dashboard
 * 4. The plugin can interact with migrated/skipped entities
 */

// The Cockpit navbar renders the "Processes" link more than once (a visible
// desktop bar plus a collapsed responsive copy share the same href), so a bare
// a[href="#/processes"] locator matches multiple elements and trips Playwright's
// strict mode. Scope to the visible match so the choice is deterministic
// regardless of DOM order (the hidden responsive copy may come first).
const processesLink = (page: Page) => page.locator('a[href="#/processes"]:visible').first();

// Navigate from the dashboard to the plugin's processes page.
async function openProcessesPage(page: Page) {
  await processesLink(page).click();
  await page.waitForURL(/#\/processes/, { timeout: 15000 });
}

test.describe('Cockpit Plugin E2E', () => {
  test.describe.configure({ mode: 'serial' });
  // Cold CI containers need time for the login round-trip plus the Angular
  // bootstrap; give each test a generous budget. Firefox initialises the
  // Angular runtime noticeably slower than Chromium on cold CI runners, so
  // the budget must cover the worst-case beforeEach (up to 60 s for the login
  // form + up to 60 s for the processes-link readiness gate + navigation
  // overhead) and still leave meaningful headroom for the test body itself.
  test.setTimeout(180000);

  test.beforeEach(async ({ page }) => {
    // Playwright's default `page` fixture is function-scoped: every test gets a
    // fresh, cookie-less context, so we authenticate from scratch each time.
    // Wait only for DOMContentLoaded (not the full `load` event) so that we
    // start polling for the Angular-rendered login form as soon as the DOM is
    // parsed, rather than waiting for every sub-resource to finish loading.
    await page.goto('/camunda/app/cockpit/default/', { waitUntil: 'domcontentloaded' });

    // Log in with the Camunda 7 demo user. The login form is served by the same
    // Angular app, so wait for it explicitly rather than racing isVisible().
    // Use a 60 s budget to accommodate Firefox's slower Angular bootstrap on
    // cold CI containers (the previous 30 s budget caused intermittent failures
    // in Firefox, see #1955).
    const usernameInput = page.locator('input[ng-model="username"]');
    await usernameInput.waitFor({ state: 'visible', timeout: 60000 });
    await usernameInput.fill('demo');
    await page.fill('input[ng-model="password"]', 'demo');
    await page.click('button[type="submit"]');

    // Readiness gate: the Cockpit SPA must finish bootstrapping and render its
    // navigation before any test interacts with it. If a plugin bundle fails to
    // load (e.g. an unresolved bare module specifier), the SPA hangs on its
    // loading spinner and this wait fails fast with a clear, actionable signal
    // instead of a vague mid-test click timeout.
    await processesLink(page).waitFor({ state: 'visible', timeout: 60000 });
  });
  test('should load Camunda Cockpit successfully', async ({ page }) => {
    await page.screenshot({ path: 'test-results/after-login.png', fullPage: true });

    // Cockpit URL may or may not include #/dashboard depending on version/timing
    await expect(page).toHaveURL(/\/camunda\/app\/cockpit\//);
    await expect(page).toHaveTitle(/Cockpit/);

    // The static <title> is present even on the login page; asserting the nav
    // rendered is what actually proves the SPA bootstrapped successfully.
    await expect(processesLink(page)).toBeVisible();
  });

  test('should display the migrator plugin on processes page', async ({ page }) => {
    await openProcessesPage(page);

    // Wait for the plugin to load - look for the plugin title
    const pluginTitle = page.locator('h1.section-title:has-text("Camunda 7 to 8 Data Migrator")');
    await pluginTitle.waitFor({ timeout: 10000 });

    // Verify the plugin title is visible
    await expect(pluginTitle).toBeVisible();

    // Take a screenshot for verification
    await page.screenshot({ path: 'test-results/plugin-on-processes-page.png', fullPage: true });
  });

  test('should display migrated and skipped entity tabs', async ({ page }) => {
    await openProcessesPage(page);

    // Look for the radio buttons for skipped/migrated
    const skippedRadio = page.locator('input[type="radio"][value="skipped"]');
    const migratedRadio = page.locator('input[type="radio"][value="migrated"]');
    await skippedRadio.waitFor({ state: 'visible', timeout: 10000 });

    // Verify both radio buttons are visible
    await expect(skippedRadio).toBeVisible();
    await expect(migratedRadio).toBeVisible();

    // Verify the labels are present
    await expect(page.locator('text=Skipped')).toBeVisible();
    await expect(page.locator('text=Migrated')).toBeVisible();

    // Take a screenshot
    await page.screenshot({ path: 'test-results/plugin-tabs.png', fullPage: true });
  });

  test('should be able to switch between entity types', async ({ page }) => {
    await openProcessesPage(page);

    // Switch to History mode to access the entity type selector
    const historyRadio = page.locator('input[type="radio"][value="history"]');
    await expect(historyRadio).toBeVisible({ timeout: 10000 });
    await expect(historyRadio).toBeEnabled({ timeout: 10000 });
    await historyRadio.click();

    // Look for the entity type selector dropdown
    const entityTypeSelector = page.locator('select#type-selector');

    // Verify the selector is visible
    await expect(entityTypeSelector).toBeVisible({ timeout: 10000 });

    // Take screenshot of the dropdown
    await page.screenshot({ path: 'test-results/entity-type-selector.png', fullPage: true });

    // Verify we can see options
    const options = entityTypeSelector.locator('option');
    const optionsCount = await options.count();

    expect(optionsCount).toBeGreaterThan(0);
  });

  test('should display 6 skipped process instances with correct columns and data', async ({ page }) => {
    await openProcessesPage(page);

    // Select "Skipped" radio button (should be selected by default, but ensure it)
    const skippedRadio = page.locator('input[type="radio"][value="skipped"]');
    await expect(skippedRadio).toBeVisible({ timeout: 10000 });
    await skippedRadio.click();

    // Find the table
    const table = page.locator('view[data-plugin-id="camunda-7-to-8-data-migrator"] table');
    await expect(table).toBeVisible();
    await table.locator('tbody tr').first().waitFor({ state: 'visible', timeout: 10000 });

    // Verify table headers contain the expected columns
    const headerRow = table.locator('thead tr');
    await expect(headerRow.locator('th:has-text("Process Instance ID")')).toBeVisible();
    await expect(headerRow.locator('th:has-text("Process Definition Key")')).toBeVisible();
    await expect(headerRow.locator('th:has-text("Skip Reason")')).toBeVisible();

    const expectedSkippedCount = 6;
    const dataRows = table.locator('tbody tr');
    await expect(dataRows).toHaveCount(expectedSkippedCount, { timeout: 15000 });

    // Verify each row has the expected data
    for (let i = 0; i < expectedSkippedCount; i++) {
      const row = dataRows.nth(i);

      // Get cells in the row
      const cells = row.locator('td');

      // Process Instance ID should be a UUID (format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
      const processInstanceId = await cells.nth(0).textContent();
      expect(processInstanceId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);

      // Process Definition Key should be "invoice"
      const processDefinitionKey = await cells.nth(1).textContent();
      expect(processDefinitionKey?.trim()).toBe('invoice');

      // Skip Reason should contain the expected error message
      const skipReason = await cells.nth(2).textContent();
      expect(skipReason).toContain('No C8 deployment found for process ID [invoice] required for instance with C7 ID');
    }

    // Take a screenshot for verification
    await page.screenshot({ path: 'test-results/skipped-instances-table.png', fullPage: true });
  });

  test('should render plugin UI elements without errors', async ({ page }) => {
    // Listen for console errors before navigation
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await openProcessesPage(page);

    // Wait for plugin to fully render
    await page.locator('h1:has-text("Camunda 7 to 8 Data Migrator")').waitFor({ timeout: 10000 });
    await page
      .locator('input[type="radio"][value="skipped"]')
      .waitFor({ state: 'visible', timeout: 10000 });

    // Take final screenshot
    await page.screenshot({ path: 'test-results/plugin-loaded.png', fullPage: true });

    // Verify no React errors or critical JavaScript errors
    const hasReactErrors = errors.some(err =>
      err.includes('React') ||
      err.includes('TypeError') ||
      err.includes('ReferenceError') ||
      err.includes('is not a function')
    );

    // Log errors for debugging if any exist
    if (errors.length > 0) {
      console.log('Console errors detected:', errors);
    }

    expect(hasReactErrors).toBeFalsy();
  });
});
