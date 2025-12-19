/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test, expect } from '@playwright/test';

/**
 * Helper function to navigate to the first decision instance details page
 */
async function navigateToFirstDecisionInstance(page: any) {
  // Navigate to decisions page
  const decisionsLink = page.locator('nav a:has-text("Decisions"), a[href*="/decisions"], a[title*="Decisions"]').first();
  await decisionsLink.waitFor({ state: 'visible', timeout: 10000 });
  await decisionsLink.click();

  // Wait for the decisions page to load
  await page.waitForURL('**/decisions**', { timeout: 10000 });
  await page.waitForTimeout(3000);

  // Find the first decision instance and click on its key/ID to open details
  const firstDecisionLink = page.locator(
    '[data-testid="decision-instance-link"], ' +
    'a[href*="/decisions/"], ' +
    '[data-testid="data-list"] a, ' +
    'table tbody tr td:first-child a'
  ).first();
  await firstDecisionLink.waitFor({ state: 'visible', timeout: 10000 });
  await firstDecisionLink.click();

  // Wait for the decision instance detail page to load
  await page.waitForURL('**/decisions/**', { timeout: 10000 });
  await page.waitForTimeout(3000);
}

/**
 * Helper function to navigate to a specific decision instance by decision name
 */
async function navigateToDecisionByName(page: any, decisionName: string) {
  // Navigate to decisions page
  const decisionsLink = page.locator('nav a:has-text("Decisions"), a[href*="/decisions"], a[title*="Decisions"]').first();
  await decisionsLink.waitFor({ state: 'visible', timeout: 10000 });
  await decisionsLink.click();

  // Wait for the decisions page to load
  await page.waitForURL('**/decisions**', { timeout: 10000 });
  await page.waitForTimeout(3000);

  // Find the decision row containing the specified decision name
  // Look for a row that contains the decision name and then find the link within it
  const decisionRow = page.locator(`[data-testid="data-list"] > div:has-text("${decisionName}"), table tbody tr:has-text("${decisionName}")`).first();
  await decisionRow.waitFor({ state: 'visible', timeout: 10000 });

  // Find the clickable link within that row
  const decisionLink = decisionRow.locator('a[href*="/decisions/"]').first();
  await decisionLink.waitFor({ state: 'visible', timeout: 10000 });
  await decisionLink.click();

  // Wait for the decision instance detail page to load
  await page.waitForURL('**/decisions/**', { timeout: 10000 });
  await page.waitForTimeout(3000);
}

/**
 * E2E tests for Camunda Operate - Decision Instances
 *
 * This test suite validates:
 * 1. Operate login and authentication
 * 2. Decision instances list displays correct number of instances (12)
 * 3. Decision instance details page displays:
 *    - DMN decision table with name, hit policy, inputs, outputs, and rules
 *    - Input variables with specific values
 *    - Output variables with rule numbers and values
 * 4. DRD (Decision Requirements Diagram) panel displays:
 *    - Diagram with specific decision nodes (Invoice Classification, Assign Approver Group)
 *    - Execution badges for evaluated decisions
 *    - Clickable nodes for navigation between related decisions
 * 5. No critical JavaScript errors during navigation and rendering
 */

test.describe('Operate - Decision Instances', () => {
  // Configure this test suite to run in isolation with its own browser context
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async ({ page, context }) => {
    // Clear cookies and storage to ensure clean state between tests
    await context.clearCookies();
    await context.clearPermissions();

    // Navigate directly to Camunda Operate login page
    // Note: Operate is running on port 8088 as configured in docker-compose.yml
    await page.goto('http://localhost:8088/operate/login');

    // Wait for the page to load
    await page.waitForLoadState('networkidle');

    // Check if we need to log in (login form is visible)
    const isLoginPage = await page.locator('input[name="username"]').isVisible().catch(() => false);

    if (isLoginPage) {
      // Fill in login credentials - Camunda 8 default demo user
      const usernameInput = page.locator('input[name="username"]');
      const passwordInput = page.locator('input[name="password"]');
      const submitButton = page.locator('button[type="submit"]');

      await usernameInput.waitFor({ state: 'visible', timeout: 10000 });

      await usernameInput.fill('demo');
      await passwordInput.fill('demo');

      // Take screenshot before login
      await page.screenshot({ path: 'test-results/operate-before-login.png', fullPage: true });

      await submitButton.click();

      // Wait for the dashboard to load
      await page.waitForTimeout(2000);

      // Take screenshot after login
      await page.screenshot({ path: 'test-results/operate-after-login.png', fullPage: true });
    }
  });

  test.afterEach(async ({ page, context }) => {
    // Clean up after each test to prevent state leakage
    await context.clearCookies();
    await page.close();
  });

  test('should navigate to decisions page and display 12 decision instances', async ({ page }) => {
    // Navigate to decisions page by clicking the Decisions link in the navigation
    // Try multiple possible selectors for the Decisions navigation link
    const decisionsLink = page.locator('nav a:has-text("Decisions"), a[href*="/decisions"], a[title*="Decisions"]').first();
    await decisionsLink.waitFor({ state: 'visible', timeout: 10000 });
    await decisionsLink.click();

    // Wait for the decisions page to load
    await page.waitForURL('**/decisions**', { timeout: 10000 });
    await page.waitForTimeout(3000); // Give time for data to load

    // Take screenshot of decisions page
    await page.screenshot({ path: 'test-results/operate-decisions-page.png', fullPage: true });

    // Find the table or list containing decision instances
    // Operate uses various selectors - try multiple approaches
    const decisionRows = page.locator(
      '[data-testid="data-list"] > div, ' +
      '[data-testid="decision-instance-row"], ' +
      'table tbody tr, ' +
      '[class*="ListItem"], ' +
      '[role="row"]'
    );

    // Wait for results to be visible
    await decisionRows.first().waitFor({ state: 'visible', timeout: 10000 });

    // Count the number of decision instances displayed
    const count = await decisionRows.count();

    // Verify we have exactly 12 decision instances
    expect(count).toBe(12);

    console.log(`Found ${count} decision instances on the decisions page`);
  });

  test('should open first decision instance and display details page', async ({ page }) => {
    // Navigate to decisions page
    const decisionsLink = page.locator('nav a:has-text("Decisions"), a[href*="/decisions"], a[title*="Decisions"]').first();
    await decisionsLink.waitFor({ state: 'visible', timeout: 10000 });
    await decisionsLink.click();

    // Wait for the decisions page to load
    await page.waitForURL('**/decisions**', { timeout: 10000 });
    await page.waitForTimeout(3000);

    // Find the first decision instance and click on its key/ID to open details
    const firstDecisionLink = page.locator(
      '[data-testid="decision-instance-link"], ' +
      'a[href*="/decisions/"], ' +
      '[data-testid="data-list"] a, ' +
      'table tbody tr td:first-child a'
    ).first();
    await firstDecisionLink.waitFor({ state: 'visible', timeout: 10000 });

    // Take screenshot before clicking
    await page.screenshot({ path: 'test-results/operate-before-clicking-decision.png', fullPage: true });

    await firstDecisionLink.click();

    // Wait for the decision instance detail page to load
    await page.waitForURL('**/decisions/**', { timeout: 10000 });
    await page.waitForTimeout(3000);

    // Take screenshot of decision instance details
    await page.screenshot({ path: 'test-results/operate-decision-details.png', fullPage: true });

    // Verify we're on the decision details page
    expect(page.url()).toContain('/decisions/');

    // Verify the page title
    const pageTitle = page.locator('h1');
    await expect(pageTitle).toContainText('Operate Decision Instance');

    // Verify the instance header is present
    const instanceHeader = page.locator('[data-testid="instance-header"]');
    await expect(instanceHeader).toBeVisible();

    // Verify decision name is displayed
    const decisionName = instanceHeader.locator('th:has-text("Decision Name")');
    await expect(decisionName).toBeVisible();
  });

  test('should display DMN table', async ({ page }) => {
    // Navigate to the "Assign Approver Group" decision instance specifically
    await navigateToDecisionByName(page, 'Assign Approver Group');

    // Verify the decision panel is visible
    const decisionPanel = page.locator('[data-testid="decision-panel"]');
    await expect(decisionPanel).toBeVisible();

    // Verify the decision table name is displayed
    const decisionTableName = page.locator('.decision-table-name');
    await expect(decisionTableName).toContainText('Assign Approver Group');

    // Verify the hit policy is displayed
    const hitPolicy = page.locator('.hit-policy-value');
    await expect(hitPolicy).toContainText('Collect');

    // Verify the input column header
    const inputLabel = page.locator('.input-label');
    await expect(inputLabel).toContainText('Invoice Classification');

    // Verify the output column header
    const outputLabel = page.locator('.output-label');
    await expect(outputLabel).toContainText('Approver Group');

    // Verify three rule rows exist
    const ruleRows = page.locator('.tjs-table tbody tr');
    expect(await ruleRows.count()).toBe(3);
  });

  test('should display decision inputs table with entries', async ({ page }) => {
    // Navigate to first decision instance details
    await navigateToFirstDecisionInstance(page);

    // Verify inputs section is visible
    const inputsSection = page.locator('section[aria-label="input variables"]');
    await expect(inputsSection).toBeVisible();

    // Verify the "Inputs" heading is present
    const inputsHeading = inputsSection.locator('h2:has-text("Inputs")');
    await expect(inputsHeading).toBeVisible();

    // Verify the table header columns
    const nameHeader = inputsSection.locator('.cds--structured-list-th:has-text("Name")');
    await expect(nameHeader).toBeVisible();

    const valueHeader = inputsSection.locator('.cds--structured-list-th:has-text("Value")');
    await expect(valueHeader).toBeVisible();

    // Verify the input variable name is displayed
    const inputName = inputsSection.locator('.cds--structured-list-td:has-text("Invoice Classification")');
    await expect(inputName).toBeVisible();

    // Verify the input value is displayed
    const inputValue = inputsSection.locator('.cds--structured-list-td:has-text("day-to-day expense")');
    await expect(inputValue).toBeVisible();

    console.log('Verified input: Invoice Classification = "day-to-day expense"');
  });

  test('should display decision outputs table with entries', async ({ page }) => {
    // Navigate to first decision instance details
    await navigateToFirstDecisionInstance(page);

    // Verify outputs section is visible
    const outputsSection = page.locator('section[aria-label="output variables"]');
    await expect(outputsSection).toBeVisible();

    // Verify the "Outputs" heading is present
    const outputsHeading = outputsSection.locator('h2:has-text("Outputs")');
    await expect(outputsHeading).toBeVisible();

    // Verify the table header columns
    const ruleHeader = outputsSection.locator('.cds--structured-list-th:has-text("Rule")');
    await expect(ruleHeader).toBeVisible();

    const nameHeader = outputsSection.locator('.cds--structured-list-th:has-text("Name")');
    await expect(nameHeader).toBeVisible();

    const valueHeader = outputsSection.locator('.cds--structured-list-th:has-text("Value")');
    await expect(valueHeader).toBeVisible();

    // Verify output variable names are displayed
    const outputName = outputsSection.locator('.cds--structured-list-td:has-text("Approver Group")');
    await expect(outputName.first()).toBeVisible();

    // Verify output values are displayed (both "sales" and "accounting")
    const salesValue = outputsSection.locator('.cds--structured-list-td:has-text("sales")');
    await expect(salesValue).toBeVisible();

    const accountingValue = outputsSection.locator('.cds--structured-list-td:has-text("accounting")');
    await expect(accountingValue).toBeVisible();

    // Verify rule numbers are displayed
    const rule1 = outputsSection.locator('.cds--structured-list-tbody .cds--structured-list-td').first();
    await expect(rule1).toContainText(/[12]/); // Rule 1 or 2

    console.log('Verified outputs: Approver Group = ["sales", "accounting"]');
  });

  test('should display DRD diagram with execution badges', async ({ page }) => {
    // Navigate to first decision instance details
    await navigateToFirstDecisionInstance(page);

    // Verify the DRD panel is visible
    const drdPanel = page.locator('[data-testid="drd-panel"]');
    await expect(drdPanel).toBeVisible();

    // Verify the DRD viewer and diagram content is visible
    const drdViewer = drdPanel.locator('[data-testid="drd"]');
    await expect(drdViewer).toBeVisible();

    // Verify the diagram title matches the DRD name
    const diagramTitle = drdViewer.locator('h2:has-text("Invoice Business Decisions")');
    await expect(diagramTitle).toBeVisible();

    // Wait for the main DMN diagram SVG to be rendered
    const dmnDiagram = drdPanel.locator('svg[data-element-id="invoiceBusinessDecisions"]');
    await expect(dmnDiagram).toBeVisible();

    // Verify specific decision nodes are visible in the DRD by their labels
    const invoiceClassificationNode = drdPanel.locator('text:has-text("Invoice Classification")');
    await expect(invoiceClassificationNode).toBeVisible();

    const assignApproverNode = drdPanel.locator('text:has-text("Assign Approver")');
    await expect(assignApproverNode).toBeVisible();

    // Verify badges indicating evaluated decisions (parent and child execution)
    const executionBadges = drdPanel.locator('[data-testid="state-overlay-EVALUATED"]');
    await executionBadges.first().waitFor({ state: 'visible', timeout: 10000 });

    // Verify we have badges for both decisions
    const badgeCount = await executionBadges.count();
    expect(badgeCount).toBe(2); // One for each decision in the DRD
    console.log(`Found ${badgeCount} evaluation state badges in DRD (Invoice Classification and Assign Approver Group)`);

    // Verify the badges contain checkmark icons indicating successful evaluation
    const checkmarkIcons = executionBadges.locator('svg');
    expect(await checkmarkIcons.count()).toBe(2);
  });

  test('should navigate to different decision when clicking DRD nodes', async ({ page }) => {
    // Navigate to first decision instance details
    await navigateToFirstDecisionInstance(page);

    // Get the current decision name from the header
    const currentDecisionName = await page.locator('[data-testid="instance-header"] td[title*="Assign Approver Group"]').textContent();
    console.log(`Current decision: ${currentDecisionName}`);

    // Get the DRD panel
    const drdPanel = page.locator('[data-testid="drd-panel"]');

    // Find the "Invoice Classification" decision node in the DRD
    // This is the parent/required decision that should be clickable
    const invoiceClassificationNode = drdPanel.locator('.djs-element.djs-shape[data-element-id="c7-legacy-invoiceClassification"]');
    await invoiceClassificationNode.waitFor({ state: 'visible', timeout: 10000 });

    // Get current URL before clicking
    const currentUrl = page.url();

    // Click on the Invoice Classification decision node
    await invoiceClassificationNode.click();

    // Wait for navigation to occur
    await page.waitForTimeout(3000);

    // Take screenshot after clicking decision in DRD
    await page.screenshot({ path: 'test-results/operate-after-drd-navigation.png', fullPage: true });

    // Verify that the URL has changed (navigated to another decision)
    const newUrl = page.url();
    expect(newUrl).not.toBe(currentUrl);

    // Verify the decision name changed to "Invoice Classification"
    const newDecisionName = page.locator('[data-testid="instance-header"] td[title*="Invoice Classification"]');
    await expect(newDecisionName).toBeVisible();

    console.log(`Navigation successful: from "${currentDecisionName}" to "Invoice Classification"`);
    console.log(`URL changed: ${currentUrl} -> ${newUrl}`);
  });

  test('should render decision details without errors', async ({ page }) => {
    // Listen for console errors before navigation
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    // Navigate to decisions page
    const decisionsLink = page.locator('nav a:has-text("Decisions"), a[href*="/decisions"], a[title*="Decisions"]').first();
    await decisionsLink.click();

    // Wait for the page to load
    await page.waitForTimeout(3000);

    // Open first decision
    const firstDecisionLink = page.locator(
      '[data-testid="decision-instance-link"], ' +
      'a[href*="/decisions/"], ' +
      '[data-testid="data-list"] a, ' +
      'table tbody tr td:first-child a'
    ).first();
    await firstDecisionLink.click();

    // Wait for details to fully render
    await page.waitForTimeout(3000);

    // Take final screenshot
    await page.screenshot({ path: 'test-results/operate-decision-rendered.png', fullPage: true });

    // Verify no critical JavaScript errors
    const hasCriticalErrors = errors.some(err =>
      err.includes('TypeError') ||
      err.includes('ReferenceError') ||
      err.includes('is not a function') ||
      err.includes('Cannot read')
    );

    // Log errors for debugging if any exist
    if (errors.length > 0) {
      console.log('Console errors detected:', errors);
    }

    expect(hasCriticalErrors).toBeFalsy();
  });
});

