import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptFilePath = fileURLToPath(import.meta.url);
const scriptDirectory = path.dirname(scriptFilePath);
const projectRoot = path.resolve(scriptDirectory, '..');
const allowlistPath = path.join(projectRoot, 'scripts', 'type-escape-allowlist.txt');
const sourceDir = path.join(projectRoot, 'src');

const checks = [
  { key: 'explicit-any', regex: /(:\s*any\b|\bas\s+any\b|<\s*any\s*>)/g },
  { key: 'ts-ignore', regex: /@ts-ignore\b/g },
  { key: 'ts-expect-error', regex: /@ts-expect-error\b/g },
  { key: 'ts-nocheck', regex: /@ts-nocheck\b/g },
  {
    key: 'eslint-disable-no-explicit-any',
    regex: /eslint-disable(?:-next-line|-line)?[^\n]*no-explicit-any/g,
  },
];

const allowlist = new Set(
  fs
    .readFileSync(allowlistPath, 'utf8')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#')),
);

const violations = [];

const visit = (directory) => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      visit(absolutePath);
      continue;
    }
    if (!/\.(ts|tsx|js|jsx)$/.test(entry.name)) {
      continue;
    }

    const content = fs.readFileSync(absolutePath, 'utf8');
    const lines = content.split('\n');
    const relativePath = path.relative(projectRoot, absolutePath).replaceAll('\\', '/');
    lines.forEach((line, index) => {
      const lineNumber = index + 1;
      checks.forEach(({ key, regex }) => {
        regex.lastIndex = 0;
        if (!regex.test(line)) {
          return;
        }
        const fingerprint = `${relativePath}:${lineNumber}:${key}`;
        if (!allowlist.has(fingerprint)) {
          violations.push(fingerprint);
        }
      });
    });
  }
};

visit(sourceDir);

if (violations.length > 0) {
  console.error('Type-safety escape hatch violations found:');
  violations.forEach((violation) => console.error(`  - ${violation}`));
  console.error(`\nAdd reviewed exceptions to ${path.relative(projectRoot, allowlistPath)} if truly necessary.`);
  process.exit(1);
}
