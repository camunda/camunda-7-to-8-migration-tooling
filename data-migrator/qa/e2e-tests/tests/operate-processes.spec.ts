/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test, expect } from '@playwright/test';

const OPERATE_URL = 'http://localhost:8088/operate';

/**
 * Helper: dismiss the "Here's what moved in Operate" changelog modal if present.
 */
async function dismissChangelogModal(page: any) {
  const gotItButton = page.locator('button:has-text("Got it")');
  if (await gotItButton.isVisible({ timeout: 2000 }).catch(() => false)) {
    await gotItButton.click();
    await gotItButton.waitFor({ state: 'hidden', timeout: 5000 });
  }
}

/**
 * Helper: login to Operate if needed.
 */
async function login(page: any) {
  await page.goto(`${OPERATE_URL}/login`);
  await page.waitForLoadState('networkidle');

  const isLoginPage = await page
    .locator('input[name="username"]')
    .isVisible()
    .catch(() => false);

  if (isLoginPage) {
    await page.locator('input[name="username"]').fill('demo');
    await page.locator('input[name="password"]').fill('demo');
    await page.locator('button[type="submit"]').click();
    // Wait for login to complete: either redirected away from login or login form disappears
    await page
      .locator('input[name="username"]')
      .waitFor({ state: 'hidden', timeout: 15000 });
  }

  await dismissChangelogModal(page);
}

/**
 * Helper: navigate to the processes page via the nav bar.
 */
async function goToProcesses(page: any) {
  const processesLink = page
    .locator(
      'nav a:has-text("Processes"), a[href*="/processes"], a[title*="Processes"]',
    )
    .first();
  await processesLink.waitFor({ state: 'visible', timeout: 10000 });
  await processesLink.click();
  await page.waitForURL('**/processes**', { timeout: 10000 });
  await page.waitForTimeout(3000);
}

/**
 * Helper: open a process instance by clicking its link in the process list.
 */
async function openProcessInstance(page: any, processName: string) {
  const processRow = page
    .locator(
      `[data-testid="data-list"] > div:has-text("${processName}"), table tbody tr:has-text("${processName}")`,
    )
    .first();
  await processRow.waitFor({ state: 'visible', timeout: 10000 });

  const instanceLink = processRow.locator('a[href*="/processes/"]').first();
  await instanceLink.waitFor({ state: 'visible', timeout: 10000 });
  await instanceLink.click();

  await page.waitForURL('**/processes/**', { timeout: 10000 });
  await page.waitForTimeout(3000);

  await dismissChangelogModal(page);
}

/**
 * E2E tests for Camunda Operate – Process Instances & Audit Logs
 *
 * Validates migrated process data in Operate:
 * 1. Process instances list shows migrated processes
 * 2. Instance details display BPMN diagram, instance header, and instance history
 * 3. Variables display with correct types and values (using data-testid="variable-*")
 * 4. Flow node instance history via data-testid="instance-history"
 * 5. Operations Log (audit log) — migrated audit entries with operation type, entity, actor, date
 * 6. Call activity navigation via "View all called instances" link
 * 7. Child process (Multi Instance Process) shows subprocess and scoped variables
 * 8. No critical JS errors during navigation
 */
test.describe('Operate - Process Instances & Audit Logs', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await context.clearPermissions();
    await login(page);
  });

  test.afterEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.close();
  });

  // ---------------------------------------------------------------------------
  // 1. Processes list
  // ---------------------------------------------------------------------------
  test('should display migrated process instances on the processes page', async ({
    page,
  }) => {
    await goToProcesses(page);

    await page.screenshot({
      path: 'test-results/operate-processes-page.png',
      fullPage: true,
    });

    // Verify the callingProcessId instance is listed
    const callingProcess = page.getByText('callingProcessId').first();
    await expect(callingProcess).toBeVisible({ timeout: 10000 });
  });

  // ---------------------------------------------------------------------------
  // 2. Process instance details – BPMN diagram and header
  // ---------------------------------------------------------------------------
  test('should open callingProcessId instance and display BPMN diagram', async ({
    page,
  }) => {
    await goToProcesses(page);
    await openProcessInstance(page, 'callingProcessId');

    await page.screenshot({
      path: 'test-results/operate-calling-process-details.png',
      fullPage: true,
    });

    // Verify instance header
    const instanceHeader = page.locator('[data-testid="instance-header"]');
    await expect(instanceHeader).toBeVisible({ timeout: 10000 });

    // Verify BPMN diagram is rendered
    const bpmnDiagram = page.locator('[data-testid="diagram"]');
    await expect(bpmnDiagram).toBeVisible({ timeout: 10000 });

    // Verify SVG canvas inside the diagram
    const svgCanvas = bpmnDiagram.locator('svg');
    await expect(svgCanvas.first()).toBeVisible({ timeout: 10000 });
  });

  // ---------------------------------------------------------------------------
  // 3. Variables with correct types (data-testid="variable-*")
  // ---------------------------------------------------------------------------
  test('should display variables with correct types on callingProcessId', async ({
    page,
  }) => {
    await goToProcesses(page);
    await openProcessInstance(page, 'callingProcessId');

    // Wait for the variables list
    const variablesList = page.locator('[data-testid="variables-list"]');
    await expect(variablesList).toBeVisible({ timeout: 10000 });

    await page.screenshot({
      path: 'test-results/operate-calling-process-variables.png',
      fullPage: true,
    });

    // Verify each variable exists using data-testid="variable-<name>"
    await expect(
      page.locator('[data-testid="variable-boolVar"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="variable-doubleVar"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="variable-intVar"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="variable-longVar"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="variable-stringVar"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="variable-legacyId"]'),
    ).toBeVisible();

    // Verify variable values
    const boolVarRow = page.locator('[data-testid="variable-boolVar"]');
    await expect(boolVarRow).toContainText('true');

    const doubleVarRow = page.locator('[data-testid="variable-doubleVar"]');
    await expect(doubleVarRow).toContainText('3.14');

    const intVarRow = page.locator('[data-testid="variable-intVar"]');
    await expect(intVarRow).toContainText('42');

    const longVarRow = page.locator('[data-testid="variable-longVar"]');
    await expect(longVarRow).toContainText('9999999');

    console.log(
      'Verified variables: boolVar=true, doubleVar=3.14, intVar=42, longVar=9999999, stringVar, legacyId',
    );
  });

  // ---------------------------------------------------------------------------
  // 4. Flow node instance history (audit log)
  // ---------------------------------------------------------------------------
  test('should display flow node instance history for callingProcessId', async ({
    page,
  }) => {
    await goToProcesses(page);
    await openProcessInstance(page, 'callingProcessId');

    // Verify Instance History panel
    const instanceHistory = page.locator(
      '[data-testid="instance-history"]',
    );
    await expect(instanceHistory).toBeVisible({ timeout: 10000 });

    await page.screenshot({
      path: 'test-results/operate-calling-process-history.png',
      fullPage: true,
    });

    // Verify key flow nodes appear in the history tree
    // The history shows: callingProcessId (root) > StartEvent_1, callActivityName
    await expect(
      instanceHistory.getByText('StartEvent_1'),
    ).toBeVisible();
    await expect(
      instanceHistory.getByText('callActivityName'),
    ).toBeVisible();

    console.log(
      'Verified instance history: StartEvent_1, callActivityName',
    );
  });

  // ---------------------------------------------------------------------------
  // 5. Operations Log (audit log) – verify migrated audit entries are present
  // ---------------------------------------------------------------------------
  test('should display operations log with migrated audit entries', async ({
    page,
  }) => {
    await goToProcesses(page);

    // The migrator cancels instances after migration, so enable the "Canceled" filter
    const canceledCheckbox = page.locator('label:has-text("Canceled")');
    await canceledCheckbox.waitFor({ state: 'visible', timeout: 10000 });
    await canceledCheckbox.click();
    await page.waitForLoadState('networkidle');

    // Select the second (middle) Invoice Receipt instance — it has audit log entries
    const invoiceRows = page
      .locator(
        '[data-testid="data-list"] > div:has-text("Invoice Receipt"), table tbody tr:has-text("Invoice Receipt")',
      );
    await invoiceRows.first().waitFor({ state: 'visible', timeout: 10000 });
    const instanceLink = invoiceRows.nth(2).locator('a[href*="/processes/"]').first();
    await instanceLink.waitFor({ state: 'visible', timeout: 10000 });
    await instanceLink.click();

    await page.waitForURL('**/processes/**', { timeout: 10000 });
    await page.waitForLoadState('networkidle');

    await dismissChangelogModal(page);

    // "Operations Log" is a tab button in the right panel (next to Variables, Listeners)
    const operationsLogTab = page
      .locator('button[aria-label="Operations Log"]')
      .first();
    await operationsLogTab.waitFor({ state: 'visible', timeout: 10000 });
    await operationsLogTab.click();

    await page.waitForLoadState('networkidle');

    await page.screenshot({
      path: 'test-results/operate-operations-log.png',
      fullPage: true,
    });

    // Verify the operations log table is rendered
    const dataTable = page.locator('[data-testid="data-table-container"]');
    await expect(dataTable).toBeVisible({ timeout: 10000 });

    // Verify column headers are present
    const headerLabel = dataTable.locator('.cds--table-header-label');
    await expect(headerLabel.getByText('Operation Type')).toBeVisible();
    await expect(headerLabel.getByText('Entity Type')).toBeVisible();

    // Verify at least one audit log row exists with actual data
    const tableRows = dataTable.locator('tbody tr');
    const rowCount = await tableRows.count();
    expect(rowCount).toBeGreaterThan(0);

    // Invoice Receipt has user task operations (e.g. Complete) from the migrated history
    await expect(dataTable.getByText('User task').first()).toBeVisible();

    console.log(
      `Verified operations log: ${rowCount} audit entries with User task entity type`,
    );
  });

  // ---------------------------------------------------------------------------
  // 6. Call activity — navigate to child process via "View all called instances"
  // ---------------------------------------------------------------------------
  test('should navigate from parent to child process via call activity', async ({
    page,
  }) => {
    await goToProcesses(page);
    await openProcessInstance(page, 'callingProcessId');

    const parentUrl = page.url();

    // Click "View All" link in the instance header (aria-label="View all called instances")
    const viewAllLink = page.locator(
      '[aria-label="View all called instances"], a:has-text("View All")',
    ).first();
    await viewAllLink.waitFor({ state: 'visible', timeout: 10000 });
    await viewAllLink.click();

    await page.waitForTimeout(3000);

    await page.screenshot({
      path: 'test-results/operate-called-instances.png',
      fullPage: true,
    });

    // We should now see the child process instance(s) — click on it
    const miProcessLink = page
      .locator('a[href*="/processes/"]')
      .first();
    await miProcessLink.waitFor({ state: 'visible', timeout: 10000 });
    await miProcessLink.click();

    await page.waitForTimeout(3000);

    await page.screenshot({
      path: 'test-results/operate-child-process.png',
      fullPage: true,
    });

    // Verify we're now looking at the Multi Instance Process
    const instanceHeader = page.locator('[data-testid="instance-header"]');
    await expect(
      instanceHeader.getByText('Multi Instance Process'),
    ).toBeVisible({ timeout: 10000 });

    const childUrl = page.url();
    expect(childUrl).not.toBe(parentUrl);

    console.log('Navigated from parent callingProcessId to child miProcess');
  });

  // ---------------------------------------------------------------------------
  // 7. Child process — flow node history and BPMN diagram
  // ---------------------------------------------------------------------------
  test('should display child process details with subprocess in history', async ({
    page,
  }) => {
    await goToProcesses(page);
    await openProcessInstance(page, 'Multi Instance Process');

    await page.screenshot({
      path: 'test-results/operate-mi-process-details.png',
      fullPage: true,
    });

    // Verify BPMN diagram
    const bpmnDiagram = page.locator('[data-testid="diagram"]');
    await expect(bpmnDiagram).toBeVisible({ timeout: 10000 });

    // Verify Instance History panel
    const instanceHistory = page.locator(
      '[data-testid="instance-history"]',
    );
    await expect(instanceHistory).toBeVisible({ timeout: 10000 });

    // Verify the history shows "Start Event" and "failingTask"
    await expect(
      instanceHistory.getByText('Start Event'),
    ).toBeVisible();
    await expect(
      instanceHistory.getByText('failingTask'),
    ).toBeVisible();

    console.log(
      'Verified child process history: Start Event, failingTask',
    );
  });

  // ---------------------------------------------------------------------------
  // 8. Child process — scoped variables
  // ---------------------------------------------------------------------------
  test('should display scoped variables on child process', async ({
    page,
  }) => {
    await goToProcesses(page);
    await openProcessInstance(page, 'Multi Instance Process');

    // Wait for variables list
    const variablesList = page.locator('[data-testid="variables-list"]');
    await expect(variablesList).toBeVisible({ timeout: 10000 });

    await page.screenshot({
      path: 'test-results/operate-mi-process-variables.png',
      fullPage: true,
    });

    // The child process should have its own legacyId variable
    await expect(
      page.locator('[data-testid="variable-legacyId"]'),
    ).toBeVisible();

    console.log('Verified scoped variables: legacyId');
  });

  // ---------------------------------------------------------------------------
  // 9. No critical JS errors
  // ---------------------------------------------------------------------------
  test('should render process details without critical errors', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await goToProcesses(page);
    await openProcessInstance(page, 'callingProcessId');

    // Wait for full render
    await page.waitForTimeout(3000);

    await page.screenshot({
      path: 'test-results/operate-process-no-errors.png',
      fullPage: true,
    });

    const hasCriticalErrors = errors.some(
      (err) =>
        err.includes('TypeError') ||
        err.includes('ReferenceError') ||
        err.includes('is not a function') ||
        err.includes('Cannot read'),
    );

    if (errors.length > 0) {
      console.log('Console errors detected:', errors);
    }

    expect(hasCriticalErrors).toBeFalsy();
  });
});
