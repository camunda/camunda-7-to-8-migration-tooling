const fs = require('fs');
const path = require('path');

const PATTERN_ROOT = './';
const OUTPUT_FILE = 'PATTERN_CATALOG.md';
const PATTERN_ROOT_README = './README.md';

const IGNORED_FOLDERS = new Set(['code-examples']);


// Read README.md from a folder if available
function getFolderReadme(folderPath) {
  const readmePath = path.join(folderPath, 'README.md');
  if (fs.existsSync(readmePath)) {
    const content = fs.readFileSync(readmePath, 'utf-8');
    const titleMatch = content.match(/^#\s+(.+)/m);
    const title = titleMatch ? titleMatch[1].trim() : null;
    const intro = extractIntro(content);
    return { title, intro };
  }
  return { title: null, intro: '' };
}


// Extract first non-heading paragraph
function extractIntro(content) {
  const lines = content.split('\n').filter(line => line.trim() !== '');
  for (const line of lines) {
    if (!line.startsWith('#')) return line.trim();
  }
  return '';
}

function stripPrefix(name) {
  return name.replace(/^\d+[_-]/, '').replace(/\.md$/, '').replace(/[-_]/g, ' ');
}

// Extract the first heading from a markdown file
function extractTitle(content, fallbackFilename) {
  const match = content.match(/^#\s+(.+)/m);
  if (match) return match[1].trim();
  return fallbackFilename.replace(/\.md$/, '').replace(/[-_]/g, ' ');
}


// Recursively walk folders and build catalog
function buildCatalog(dir, depth = 1) {
  let output = '';
  const entries = fs.readdirSync(dir, { withFileTypes: true });

  // Separate folders and markdown files
  const folders = entries
    .filter(e => e.isDirectory() && !IGNORED_FOLDERS.has(e.name));
  const files = entries
    .filter(e => e.isFile() && e.name.endsWith('.md') && e.name.toLowerCase() !== 'readme.md');

  // Heading for the current folder
  const isTopLevel = path.resolve(dir) === path.resolve(PATTERN_ROOT);
  const { title: readmeTitle, intro: folderIntro } = getFolderReadme(dir);
  
  if (!isTopLevel) {
    const sectionTitle = readmeTitle || stripPrefix(path.basename(dir));
    const heading = `${'#'.repeat(depth)} ${sectionTitle}`;

    output += `\n${heading}\n\n`;
    if (folderIntro) output += `${folderIntro}\n\n`;
  }

  if (files.length > 0) output += `Patterns:\n\n`;

  // List all .md files
  for (const file of files) {
    const filePath = path.join(dir, file.name);
    const content = fs.readFileSync(filePath, 'utf-8');
    const title = extractTitle(content, stripPrefix(file.name));
    const relativePath = filePath.replace(/^\.\/|\\/g, '/');
    output += `- [${title}](${relativePath})\n`;
  }

  // Recurse into subfolders
  for (const folder of folders) {
    const subfolderPath = path.join(dir, folder.name);
    output += buildCatalog(subfolderPath, depth + 1);
  }

  return output;
}

function injectCatalogIntoReadme(catalogMarkdown) {
  const tagStart = '<!-- BEGIN-CATALOG -->';
  const tagEnd = '<!-- END-CATALOG -->';

  if (!fs.existsSync(PATTERN_ROOT_README)) {
    console.warn('README.md not found');
    return;
  }

  let readme = fs.readFileSync(PATTERN_ROOT_README, 'utf-8');
  const regex = new RegExp(`${tagStart}[\\s\\S]*?${tagEnd}`, 'm');
  const injected = `${tagStart}\n\n${catalogMarkdown.trim()}\n\n${tagEnd}`;
  if (readme.match(regex)) {
    readme = readme.replace(regex, injected);
  } else {
    // If markers don't exist, append to end
    readme += `\n\n## Pattern Catalog\n\n${injected}`;
  }

  fs.writeFileSync(PATTERN_ROOT_README, readme);
  console.log('✅ Pattern catalog injected into README.md');
}


// Main
function main() {
  const catalogContent = buildCatalog(PATTERN_ROOT);
  //fs.writeFileSync(OUTPUT_FILE, catalogContent);
  injectCatalogIntoReadme(catalogContent);
  console.log(`✅ ${OUTPUT_FILE} generated.`);
}

main();
