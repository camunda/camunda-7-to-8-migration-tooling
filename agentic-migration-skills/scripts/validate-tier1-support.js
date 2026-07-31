#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..", "..");
const matrixPath = path.resolve(__dirname, "..", "support-matrix.json");
const readmePath = path.resolve(__dirname, "..", "README.md");

const requiredHarnesses = [
  "github-copilot",
  "openai-codex",
  "cursor",
  "gemini-cli",
  "cline",
];

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function main() {
  const matrix = readJson(matrixPath);
  const readme = fs.readFileSync(readmePath, "utf8");

  const canonicalSkillAbsolute = path.resolve(
    repoRoot,
    matrix.canonicalSkill,
  );
  assert(
    fs.existsSync(canonicalSkillAbsolute),
    `Canonical skill does not exist: ${matrix.canonicalSkill}`,
  );

  const byName = new Map(matrix.harnesses.map((item) => [item.name, item]));
  for (const harness of requiredHarnesses) {
    assert(
      byName.has(harness),
      `Missing tier 1 harness in support-matrix.json: ${harness}`,
    );
    const entry = byName.get(harness);
    assert(
      entry.tier === "tier-1",
      `Harness ${harness} must be tier-1, found: ${entry.tier}`,
    );
    assert(
      entry.status === "supported",
      `Harness ${harness} must be supported, found: ${entry.status}`,
    );
    assert(
      entry.distribution?.projectPath &&
        entry.distribution?.userPath,
      `Harness ${harness} must define projectPath and userPath`,
    );
    assert(
      entry.distribution?.installCommand,
      `Harness ${harness} must define installCommand`,
    );
  }

  for (const harness of requiredHarnesses) {
    assert(
      readme.includes(`**${harness}**`),
      `README.md is missing install section marker for ${harness}`,
    );
  }

  process.stdout.write("Tier 1 harness support validation passed.\n");
}

try {
  main();
} catch (error) {
  process.stderr.write(`Validation failed: ${error.message}\n`);
  process.exit(1);
}
