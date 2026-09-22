/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
const fs = require('fs');
const path = require('path');

const PATTERN_ROOT = './';
const OUTPUT_FILE = 'ALL_IN_ONE.md';
const IGNORED_FOLDERS = new Set(['code-examples']);

function stripPrefix(name) {
  return name.replace(/^\d+[_-]/, '').replace(/\.md$/, '').replace(/[-_]/g, ' ');
}

function normalizeHeadingLevels(content, levelOffset = 0) {
  return content.replace(/^#{1,6}\s+/gm, match => {
    const newLevel = Math.min(match.length + levelOffset, 6);
    return '#'.repeat(newLevel) + ' ';
  });
}

function isExternalLink(target) {
  return (
    target.startsWith('#') ||
    target.startsWith('/') ||
    target.startsWith('//') ||
    /^[a-z][a-z\d+.-]*:/i.test(target)
  );
}

function rewriteRelativeLinks(content, sourceDir) {
  return content.replace(/(\]\()([^)]+)(\))/g, (match, prefix, destination, suffix) => {
    const leadingWhitespace = destination.match(/^\s*/)[0];
    const trailingWhitespace = destination.match(/\s*$/)[0];
    const trimmedDestination = destination.trim();
    const destinationMatch = /^(\S+)([\s\S]*)$/.exec(trimmedDestination);

    if (!destinationMatch || isExternalLink(destinationMatch[1])) {
      return match;
    }

    const linkTarget = destinationMatch[1];
    const suffixStart = linkTarget.search(/[?#]/);
    const relativePath = suffixStart === -1 ? linkTarget : linkTarget.slice(0, suffixStart);

    if (!relativePath) {
      return match;
    }

    const targetPath = path.resolve(sourceDir, relativePath);
    const generatedPath = path.relative(PATTERN_ROOT, targetPath).split(path.sep).join('/');
    const targetSuffix = suffixStart === -1 ? '' : linkTarget.slice(suffixStart);

    return `${prefix}${leadingWhitespace}${generatedPath}${targetSuffix}${destinationMatch[2]}${trailingWhitespace}${suffix}`;
  });
}

function extractTitleFromFile(content, fallbackFilename) {
  const match = content.match(/^#\s+(.+)/m);
  if (match) return match[1].trim();
  return fallbackFilename.replace(/\.md$/, '').replace(/[-_]/g, ' ');
}

function getFolderReadme(folderPath) {
  const readmePath = path.join(folderPath, 'README.md');
  if (fs.existsSync(readmePath)) {
    const content = fs.readFileSync(readmePath, 'utf-8');
    const titleMatch = content.match(/^#\s+(.+)/m);
    return {
      title: titleMatch ? titleMatch[1].trim() : null
    };
  }
  return { title: null };
}

function collectAllContent(dir, depth = 1) {
  let content = '';
  const entries = fs.readdirSync(dir, { withFileTypes: true });

  const folders = entries.filter(e => e.isDirectory() && !IGNORED_FOLDERS.has(e.name));
  const isTopLevel = path.resolve(dir) === path.resolve(PATTERN_ROOT);

  // Separate out README.md explicitly
  const readmeEntry = entries.find(e => e.isFile() && e.name.toLowerCase() === 'readme.md');
  const readmePath = readmeEntry ? path.join(dir, readmeEntry.name) : null;

  const files = entries.filter(e =>
    e.isFile() &&
    e.name.endsWith('.md') &&
    e.name !== OUTPUT_FILE &&
    e.name.toLowerCase() !== 'readme.md'
  );

  // Section title from README.md or fallback to folder name
  let readmeTitle = null;
  let readmeBody = null;

  if (readmePath && fs.existsSync(readmePath)) {
    const raw = fs.readFileSync(readmePath, 'utf-8');
    const match = raw.match(/^#\s+(.*)/m);
    readmeTitle = match ? match[1].trim() : null;
    readmeBody = raw.replace(/^#\s+.*\n?/, '').trim();
  }

  const sectionTitle = readmeTitle || stripPrefix(path.basename(dir));

  if (!isTopLevel) {
    content += `\n${'#'.repeat(depth)} ${sectionTitle}\n\n`;
    if (readmeBody) {
      const rewrittenReadme = rewriteRelativeLinks(readmeBody, dir);
      content += normalizeHeadingLevels(rewrittenReadme, depth).trim() + '\n\n';
    }
  }

  // Pattern files
  for (const file of files) {
    const filePath = path.join(dir, file.name);
    const raw = fs.readFileSync(filePath, 'utf-8');
    const filename = stripPrefix(file.name);

    const match = raw.match(/^#\s+(.*)/m);
    const title = match ? match[1].trim() : filename;

    const cleanedContent = rewriteRelativeLinks(raw.replace(/^#\s+.*\n?/, '').trim(), dir);

    content += `\n${'#'.repeat(depth + 1)} ${title}\n\n`;
    content += normalizeHeadingLevels(cleanedContent, depth + 1).trim() + '\n\n---\n';
  }

  // Recurse into subfolders
  for (const folder of folders) {
    content += collectAllContent(path.join(dir, folder.name), depth + 1);
  }

  return content;
}


function generateTOC(markdown) {
  const lines = markdown.split('\n');
  const tocLines = [];

  for (const line of lines) {
    const match = /^(#{2,4})\s+(.*)/.exec(line); // Only H2–H4
    if (match) {
      const level = match[1].length;
      const title = match[2].trim();
      const anchor = title
        .toLowerCase()
        .replace(/[^\w\s-]/g, '')
        .replace(/\s+/g, '-');
      tocLines.push(`${'  '.repeat(level - 2)}- [${title}](#${anchor})`);
    }
  }

  return tocLines.join('\n');
}

function main() {
  const patternsContent = collectAllContent(PATTERN_ROOT).trim();
  const toc = generateTOC(patternsContent);

  const full = `# Camunda 7 to 8 Code Conversion Patterns 
  
Automatically generated file containing all patterns from all sub folders in one document.
It is intended for quick reference and overview of all available patterns.
Do not edit this file manually, it is overwritten by a Github Action on every commit.

Patterns:

${toc}

${patternsContent}
`;

  fs.writeFileSync(path.join(PATTERN_ROOT, OUTPUT_FILE), full);
  console.log(`✅ ${OUTPUT_FILE} generated.`);
}

main();
