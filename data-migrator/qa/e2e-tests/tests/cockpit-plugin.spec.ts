/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test, expect } from '@playwright/test';

/**
 * E2E smoke test for the Cockpit plugin
 * 
 * This test validates that:
 * 1. Camunda 7 starts successfully with the plugin deployed
 * 2. The Cockpit UI is accessible
 * 3. The plugin UI is visible on the processes dashboard
 * 4. The plugin can interact with migrated/skipped entities
 */

test.describe('Cockpit Plugin E2E', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to Camunda Cockpit and login
    await page.goto('/camunda/app/cockpit/default/');
    
    // Wait for the login page to load
    await page.waitForLoadState('networkidle');
    
    // Check if we're on a login page (some Camunda instances might auto-login)
    const isLoginPage = await page.locator('input[ng-model="username"]').isVisible().catch(() => false);
    
    if (isLoginPage) {
      // Fill in login credentials - Camunda 7 default demo user
      const usernameInput = page.locator('input[ng-model="username"]');
      const passwordInput = page.locator('input[ng-model="password"]');
      const submitButton = page.locator('button[type="submit"]');
      
      // Wait for inputs to be visible
      await usernameInput.waitFor({ state: 'visible', timeout: 10000 });
      
      await usernameInput.fill('demo');
      await passwordInput.fill('demo');
      
      // Take screenshot before login
      await page.screenshot({ path: 'test-results/before-login.png', fullPage: true });
      
      await submitButton.click();
      
      // Wait a bit for the login to process
      await page.waitForTimeout(2000);
      
      // Take screenshot after clicking login
      await page.screenshot({ path: 'test-results/after-login-click.png', fullPage: true });
      
      // Wait for the dashboard to load with a longer timeout
      // The URL might redirect through several pages
      await page.waitForURL('**/cockpit/default/#/dashboard', { timeout: 30000 });
    }
  });

  test('should load Camunda Cockpit successfully', async ({ page }) => {
    // Take a screenshot after login for debugging
    await page.screenshot({ path: 'test-results/after-login.png', fullPage: true });
    
    // Verify we're on the Cockpit dashboard
    await expect(page).toHaveURL(/.*cockpit.*dashboard/);
    
    // Verify the page title
    await expect(page).toHaveTitle(/Cockpit/);
  });

  test('should display the migrator plugin on processes page', async ({ page }) => {
    // Navigate to processes page
    await page.click('a[href="#/processes"]');
    await page.waitForURL('**/#/processes?pdSearchQuery=%5B%5D');
    
    // Wait for the plugin to load - look for the plugin title
    const pluginTitle = page.locator('h1.section-title:has-text("Camunda 7 to 8 Data Migrator")');
    await pluginTitle.waitFor({ timeout: 10000 });
    
    // Verify the plugin title is visible
    await expect(pluginTitle).toBeVisible();
    
    // Take a screenshot for verification
    await page.screenshot({ path: 'test-results/plugin-on-processes-page.png', fullPage: true });
  });

  test('should display migrated and skipped entity tabs', async ({ page }) => {
    // Navigate to processes page
    await page.click('a[href="#/processes"]');
    await page.waitForURL('**/#/processes?pdSearchQuery=%5B%5D');
    
    // Wait for the plugin to render
    await page.waitForTimeout(2000); // Give React time to render
    
    // Look for the radio buttons for skipped/migrated
    const skippedRadio = page.locator('input[type="radio"][value="skipped"]');
    const migratedRadio = page.locator('input[type="radio"][value="migrated"]');
    
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
    // Navigate to processes page
    await page.click('a[href="#/processes"]');
    await page.waitForURL('**/#/processes?pdSearchQuery=%5B%5D');
    
    // Wait for plugin to load
    await page.waitForTimeout(2000);
    
    // Switch to History mode to access the entity type selector
    const historyRadio = page.locator('input[type="radio"][value="history"]');
    await historyRadio.click();
    
    // Wait for the dropdown to appear
    await page.waitForTimeout(500);
    
    // Look for the entity type selector dropdown
    const entityTypeSelector = page.locator('select#type-selector');
    
    // Verify the selector is visible
    await expect(entityTypeSelector).toBeVisible();
    
    // Take screenshot of the dropdown
    await page.screenshot({ path: 'test-results/entity-type-selector.png', fullPage: true });
    
    // Verify we can see options
    const options = entityTypeSelector.locator('option');
    const optionsCount = await options.count();
    
    expect(optionsCount).toBeGreaterThan(0);
  });

  test('should display 6 skipped process instances with correct columns and data', async ({ page }) => {
    // Navigate to processes page
    await page.click('a[href="#/processes"]');
    await page.waitForURL('**/#/processes?pdSearchQuery=%5B%5D');

    // Wait for plugin to load
    await page.waitForTimeout(2000);

    // Select "Skipped" radio button (should be selected by default, but ensure it)
    const skippedRadio = page.locator('input[type="radio"][value="skipped"]');
    await skippedRadio.click();

    // Wait for data to load
    await page.waitForTimeout(2000);

    // Find the table
    const table = page.locator('view[data-plugin-id="camunda-7-to-8-data-migrator"] table');
    await expect(table).toBeVisible();

    // Verify table headers contain the expected columns
    const headerRow = table.locator('thead tr');
    await expect(headerRow.locator('th:has-text("Process Instance ID")')).toBeVisible();
    await expect(headerRow.locator('th:has-text("Process Definition Key")')).toBeVisible();
    await expect(headerRow.locator('th:has-text("Skip Reason")')).toBeVisible();

    // Get all data rows (excluding header)
    const dataRows = table.locator('tbody tr');
    const rowCount = await dataRows.count();

    // Verify we have exactly 6 rows
    expect(rowCount).toBe(6);

    // Verify each row has the expected data
    for (let i = 0; i < rowCount; i++) {
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
    
    // Navigate to processes page  
    await page.click('a[href="#/processes"]');
    await page.waitForURL('**/#/processes?pdSearchQuery=%5B%5D');
    
    // Wait for plugin to fully render
    await page.locator('h1:has-text("Camunda 7 to 8 Data Migrator")').waitFor({ timeout: 10000 });
    await page.waitForTimeout(2000);
    
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
