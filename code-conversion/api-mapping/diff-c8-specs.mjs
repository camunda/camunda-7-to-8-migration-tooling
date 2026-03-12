/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Compares two versions of the C8 OpenAPI spec (c8_8.js) and outputs a
 * markdown summary of what changed: endpoints added/removed, schemas
 * added/removed, and the current stats for updating mapping_7_23_to_8_8.jsx.
 *
 * Usage:
 *   node diff-c8-specs.mjs <old-c8-spec.js> <new-c8-spec.js> [--output <file>]
 *
 * Both arguments must be ES module files that export `c8_8`.
 *
 * Example — compare the committed version with a freshly generated one:
 *   git show HEAD:code-conversion/api-mapping/src/openapi/camunda8/c8_8.js > /tmp/old_c8_8.js
 *   node diff-c8-specs.mjs /tmp/old_c8_8.js src/openapi/camunda8/c8_8.js
 */

import { pathToFileURL } from "url";
import { resolve, join } from "path";
import { writeFileSync, readdirSync, readFileSync, copyFileSync, unlinkSync, mkdtempSync } from "fs";
import { tmpdir } from "os";

// ---------------------------------------------------------------------------
// CLI argument parsing
// ---------------------------------------------------------------------------
const args = process.argv.slice(2);
const positional = [];
let outputPath = null;

for (let i = 0; i < args.length; i++) {
	if (args[i] === "--output" && args[i + 1]) {
		outputPath = args[++i];
	} else if (!args[i].startsWith("-")) {
		positional.push(args[i]);
	}
}

if (positional.length < 2) {
	console.error(
		"Usage: node diff-c8-specs.mjs <old-c8-spec.js> <new-c8-spec.js> [--output <file>]",
	);
	process.exit(1);
}

const [oldPath, newPath] = positional.map((p) => resolve(p));

// ---------------------------------------------------------------------------
// Load specs via dynamic import
// ---------------------------------------------------------------------------
const tempFiles = [];

async function loadSpec(filePath) {
	// If the file is a .js outside an ESM package, Node will reject the
	// `export` keyword.  Work around this by copying to a temp .mjs file.
	let importPath = filePath;
	if (filePath.endsWith(".js")) {
		const dir = mkdtempSync(join(tmpdir(), "diff-c8-"));
		const tmp = join(dir, "spec.mjs");
		copyFileSync(filePath, tmp);
		importPath = tmp;
		tempFiles.push(tmp);
	}
	const url = pathToFileURL(importPath).href;
	const mod = await import(url);
	return mod.c8_8;
}

process.on("exit", () => {
	for (const f of tempFiles) {
		try { unlinkSync(f); } catch { /* ignore */ }
	}
});

const oldSpec = await loadSpec(oldPath);
const newSpec = await loadSpec(newPath);

// ---------------------------------------------------------------------------
// Extraction helpers
// ---------------------------------------------------------------------------
function extractOperations(spec) {
	const ops = new Map();
	for (const [path, methods] of Object.entries(spec.paths)) {
		for (const [method, details] of Object.entries(methods)) {
			const key = `${method.toUpperCase()} ${path}`;
			ops.set(key, {
				tags: details.tags || [],
				operationId: details.operationId || "",
				summary: details.summary || "",
				parameters: details.parameters || [],
				requestBody: details.requestBody,
			});
		}
	}
	return ops;
}

function extractTags(spec) {
	const tags = new Set();
	for (const methods of Object.values(spec.paths)) {
		for (const details of Object.values(methods)) {
			(details.tags || []).forEach((t) => tags.add(t));
		}
	}
	return tags;
}

function extractSchemas(spec) {
	return new Set(Object.keys(spec.components?.schemas || {}));
}

function resolveRef(spec, ref) {
	if (!ref) return null;
	const parts = ref.replace("#/", "").split("/");
	let obj = spec;
	for (const part of parts) {
		obj = obj?.[part];
	}
	return obj;
}

function getRequestBodyProps(spec, op) {
	const content = op.requestBody?.content?.["application/json"];
	if (!content?.schema) return [];
	let schema = content.schema;
	if (schema.$ref) schema = resolveRef(spec, schema.$ref);
	if (!schema) return [];

	// Merge allOf if present
	let props = {};
	if (schema.allOf) {
		for (const item of schema.allOf) {
			const resolved = item.$ref ? resolveRef(spec, item.$ref) : item;
			if (resolved?.properties) Object.assign(props, resolved.properties);
		}
	} else if (schema.properties) {
		props = schema.properties;
	}
	return Object.keys(props).sort();
}

// ---------------------------------------------------------------------------
// Mapping target validation
// ---------------------------------------------------------------------------
function validateMappingTargets(spec) {
	const mappingDir = join(
		resolve("."),
		"src/mappings/mapping_details_7_23_to_8_8",
	);
	let jsxFiles;
	try {
		jsxFiles = readdirSync(mappingDir).filter((f) => f.endsWith(".jsx"));
	} catch {
		return null; // mapping dir not found — skip validation
	}

	const specPaths = new Set(Object.keys(spec.paths));
	const targetPathRegex =
		/target:\s*\{[^}]*path:\s*"([^"]+)"[^}]*operation:\s*"([^"]+)"/g;
	const broken = [];

	for (const file of jsxFiles) {
		const content = readFileSync(join(mappingDir, file), "utf8");
		let match;
		while ((match = targetPathRegex.exec(content)) !== null) {
			const targetPath = match[1];
			if (targetPath && !specPaths.has(targetPath)) {
				broken.push({ file, targetPath });
			}
		}
	}
	return broken;
}

// ---------------------------------------------------------------------------
// Diff computation
// ---------------------------------------------------------------------------
const oldOps = extractOperations(oldSpec);
const newOps = extractOperations(newSpec);
const oldTags = extractTags(oldSpec);
const newTags = extractTags(newSpec);
const oldSchemas = extractSchemas(oldSpec);
const newSchemas = extractSchemas(newSpec);

const addedOps = [...newOps.keys()]
	.filter((k) => !oldOps.has(k))
	.sort();
const removedOps = [...oldOps.keys()]
	.filter((k) => !newOps.has(k))
	.sort();
const addedTags = [...newTags].filter((t) => !oldTags.has(t)).sort();
const removedTags = [...oldTags].filter((t) => !newTags.has(t)).sort();
const addedSchemas = [...newSchemas]
	.filter((s) => !oldSchemas.has(s))
	.sort();
const removedSchemas = [...oldSchemas]
	.filter((s) => !newSchemas.has(s))
	.sort();

// Detect request body property changes on endpoints that exist in both specs
const changedOps = [];
for (const [key, newDetails] of newOps) {
	if (!oldOps.has(key)) continue;
	const oldDetails = oldOps.get(key);
	const oldProps = getRequestBodyProps(oldSpec, oldDetails);
	const newProps = getRequestBodyProps(newSpec, newDetails);
	const addedProps = newProps.filter((p) => !oldProps.includes(p));
	const removedProps = oldProps.filter((p) => !newProps.includes(p));
	if (addedProps.length > 0 || removedProps.length > 0) {
		changedOps.push({ key, addedProps, removedProps });
	}
}

// Validate mapping targets against the NEW spec
const brokenTargets = validateMappingTargets(newSpec);

// ---------------------------------------------------------------------------
// Output
// ---------------------------------------------------------------------------
const lines = [];
const ln = (s = "") => lines.push(s);

ln("# C8 OpenAPI Spec Diff");
ln();
ln(`Old: \`${oldPath}\``);
ln(`New: \`${newPath}\``);
ln();

ln("## Stats");
ln();
ln("| Metric | Old | New | Change |");
ln("|---|---|---|---|");
ln(
	`| Tags (endpoint groups) | ${oldTags.size} | ${newTags.size} | ${newTags.size - oldTags.size >= 0 ? "+" : ""}${newTags.size - oldTags.size} |`,
);
ln(
	`| Unique paths | ${Object.keys(oldSpec.paths).length} | ${Object.keys(newSpec.paths).length} | ${Object.keys(newSpec.paths).length - Object.keys(oldSpec.paths).length >= 0 ? "+" : ""}${Object.keys(newSpec.paths).length - Object.keys(oldSpec.paths).length} |`,
);
ln(
	`| Operations | ${oldOps.size} | ${newOps.size} | ${newOps.size - oldOps.size >= 0 ? "+" : ""}${newOps.size - oldOps.size} |`,
);
ln(
	`| Schemas | ${oldSchemas.size} | ${newSchemas.size} | ${newSchemas.size - oldSchemas.size >= 0 ? "+" : ""}${newSchemas.size - oldSchemas.size} |`,
);
ln();
ln(
	`> **Update mapping_7_23_to_8_8.jsx** — C8 stats should read: **${newTags.size} endpoint groups and ${newOps.size} endpoints**.`,
);

if (addedTags.length > 0) {
	ln();
	ln(`## Added Tags (+${addedTags.length})`);
	ln();
	addedTags.forEach((t) => ln(`- ${t}`));
}

if (removedTags.length > 0) {
	ln();
	ln(`## Removed Tags (-${removedTags.length})`);
	ln();
	removedTags.forEach((t) => ln(`- ${t}`));
}

if (addedOps.length > 0) {
	ln();
	ln(`## Added Endpoints (+${addedOps.length})`);
	ln();
	addedOps.forEach((op) => {
		const tags = newOps.get(op).tags.join(", ");
		ln(`- \`${op}\` [${tags}]`);
	});
}

if (removedOps.length > 0) {
	ln();
	ln(`## Removed Endpoints (-${removedOps.length})`);
	ln();
	removedOps.forEach((op) => {
		const tags = oldOps.get(op).tags.join(", ");
		ln(`- \`${op}\` [${tags}]`);
	});
}

if (changedOps.length > 0) {
	ln();
	ln(`## Changed Endpoints (request body properties)`);
	ln();
	for (const { key, addedProps, removedProps } of changedOps) {
		ln(`### \`${key}\``);
		if (addedProps.length)
			ln(`- Added: ${addedProps.map((p) => `\`${p}\``).join(", ")}`);
		if (removedProps.length)
			ln(`- Removed: ${removedProps.map((p) => `\`${p}\``).join(", ")}`);
		ln();
	}
}

if (addedSchemas.length > 0) {
	ln();
	ln(`## Added Schemas (+${addedSchemas.length})`);
	ln();
	ln(addedSchemas.map((s) => `\`${s}\``).join(", "));
}

if (removedSchemas.length > 0) {
	ln();
	ln(`## Removed Schemas (-${removedSchemas.length})`);
	ln();
	ln(removedSchemas.map((s) => `\`${s}\``).join(", "));
}

if (brokenTargets && brokenTargets.length > 0) {
	ln();
	ln("## Broken Mapping Targets");
	ln();
	ln(
		"The following mapping targets reference C8 paths that do not exist in the new spec:",
	);
	ln();
	for (const { file, targetPath } of brokenTargets) {
		ln(`- \`${file}\`: target path \`${targetPath}\` not found`);
	}
}

if (
	addedOps.length === 0 &&
	removedOps.length === 0 &&
	addedTags.length === 0 &&
	removedTags.length === 0 &&
	addedSchemas.length === 0 &&
	removedSchemas.length === 0 &&
	changedOps.length === 0
) {
	ln();
	ln("No changes detected between the two specs.");
}

const output = lines.join("\n") + "\n";

if (outputPath) {
	writeFileSync(outputPath, output);
	console.log(`Summary written to ${outputPath}`);
} else {
	console.log(output);
}
