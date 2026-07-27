import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptFilePath = fileURLToPath(import.meta.url);
const scriptDirectory = path.dirname(scriptFilePath);
const projectRoot = path.resolve(scriptDirectory, '..');

const resultsPath = path.resolve(
  projectRoot,
  process.env.PLAYWRIGHT_RESULTS_PATH ?? 'test-results/playwright-report.json',
);
const failOnRetriedGreen = `${process.env.FAIL_ON_RETRIED_GREEN ?? 'true'}`.toLowerCase() === 'true';

const appendOutput = (key, value) => {
  if (!process.env.GITHUB_OUTPUT) {
    return;
  }
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
};

const appendSummary = (line) => {
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${line}\n`);
    return;
  }
  console.log(line);
};

const writeDefaultOutputs = () => {
  appendOutput('total-tests', 0);
  appendOutput('first-pass-count', 0);
  appendOutput('first-attempt-failure-count', 0);
  appendOutput('retried-green-count', 0);
  appendOutput('failed-after-retry-count', 0);
  appendOutput('first-pass-rate', '0.00');
};

if (!fs.existsSync(resultsPath)) {
  appendSummary('### Playwright first-pass reliability');
  appendSummary(`No JSON report found at \`${resultsPath}\`.`);
  writeDefaultOutputs();
  process.exit(0);
}

const report = JSON.parse(fs.readFileSync(resultsPath, 'utf8'));
const tests = [];

const collectFromSuite = (suite, parents = []) => {
  const suiteTitle = suite.title?.trim();
  const nextParents = suiteTitle ? [...parents, suiteTitle] : parents;

  for (const spec of suite.specs ?? []) {
    const specTitle = spec.title?.trim() ?? '';
    const titleParts = [...nextParents, specTitle].filter(Boolean);
    const location = spec.file
      ? `${spec.file}${spec.line ? `:${spec.line}` : ''}`
      : titleParts.join(' › ');
    for (const testCase of spec.tests ?? []) {
      tests.push({
        title: titleParts.join(' › '),
        location,
        results: testCase.results ?? [],
      });
    }
  }

  for (const child of suite.suites ?? []) {
    collectFromSuite(child, nextParents);
  }
};

for (const suite of report.suites ?? []) {
  collectFromSuite(suite, []);
}

let firstPassCount = 0;
let firstAttemptFailureCount = 0;
let retriedGreenCount = 0;
let failedAfterRetryCount = 0;
const retriedGreenDiagnostics = [];

for (const testCase of tests) {
  const attemptStatuses = testCase.results
    .map((result) => result.status)
    .filter(Boolean);

  if (attemptStatuses.length === 0) {
    continue;
  }

  const firstStatus = attemptStatuses[0];
  const finalStatus = attemptStatuses[attemptStatuses.length - 1];
  const hasRetry = attemptStatuses.length > 1;
  const firstAttemptPassed = firstStatus === 'passed';
  const retriedGreen = !firstAttemptPassed && hasRetry && finalStatus === 'passed';
  const failedAfterRetry =
    !firstAttemptPassed && finalStatus !== 'passed' && finalStatus !== 'skipped';

  if (firstAttemptPassed) {
    firstPassCount += 1;
  } else {
    firstAttemptFailureCount += 1;
  }

  if (retriedGreen) {
    retriedGreenCount += 1;
    retriedGreenDiagnostics.push(
      `${testCase.location} — ${testCase.title} (${attemptStatuses.join(' -> ')})`,
    );
  }

  if (failedAfterRetry) {
    failedAfterRetryCount += 1;
  }
}

const totalTests = firstPassCount + firstAttemptFailureCount;
const firstPassRate =
  totalTests === 0 ? 0 : (firstPassCount / totalTests) * 100;

appendSummary('### Playwright first-pass reliability');
appendSummary(`- Total tests: ${totalTests}`);
appendSummary(`- First-pass green: ${firstPassCount}`);
appendSummary(`- First-attempt failures: ${firstAttemptFailureCount}`);
appendSummary(`- Retried-green tests: ${retriedGreenCount}`);
appendSummary(`- Failed after retries: ${failedAfterRetryCount}`);
appendSummary(`- First-pass rate: ${firstPassRate.toFixed(2)}%`);

if (retriedGreenDiagnostics.length > 0) {
  appendSummary('');
  appendSummary('#### Retried-green diagnostics');
  for (const diagnostic of retriedGreenDiagnostics) {
    appendSummary(`- ${diagnostic}`);
  }
}

appendOutput('total-tests', totalTests);
appendOutput('first-pass-count', firstPassCount);
appendOutput('first-attempt-failure-count', firstAttemptFailureCount);
appendOutput('retried-green-count', retriedGreenCount);
appendOutput('failed-after-retry-count', failedAfterRetryCount);
appendOutput('first-pass-rate', firstPassRate.toFixed(2));

if (failOnRetriedGreen && retriedGreenCount > 0) {
  console.error(
    `Found ${retriedGreenCount} retried-green test(s). Flaky-green outcomes fail CI by policy.`,
  );
  process.exit(1);
}
