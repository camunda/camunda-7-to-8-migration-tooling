# Updating the C8 OpenAPI Spec

This guide walks through how to update `src/openapi/camunda8/c8_8.js` when a
new Camunda 8 patch is released, and how to verify that existing API mappings
are still correct.

## Source of truth

The OpenAPI specification lives in the Camunda monorepo:

```
zeebe/gateway-protocol/src/main/proto/rest-api.yaml
```

The API mapping always targets the last stable version (currently 8.8), so
check out the latest patch tag (e.g. `8.8.7`).

## Prerequisites

- Node.js (18+)
- `npx` (bundled with npm)
- A local checkout of the [camunda/camunda](https://github.com/camunda/camunda) repo (or just the YAML file)

Install project dependencies if you haven't already:

```bash
cd code-conversion/api-mapping
npm install
```

## Step-by-step

### 1. Convert the YAML spec to JSON

```bash
npx -y js-yaml /path/to/camunda/zeebe/gateway-protocol/src/main/proto/rest-api.yaml > /tmp/c8-spec.json
```

### 2. Back up the current spec

```bash
cp src/openapi/camunda8/c8_8.js /tmp/old_c8_8.js
```

### 3. Create the new c8_8.js

Replace the spec content while keeping the copyright header and export:

```bash
cat > src/openapi/camunda8/c8_8.js << 'HEADER'
/*
* Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
* one or more contributor license agreements. See the NOTICE file distributed
* with this work for additional information regarding copyright ownership.
* Licensed under the Camunda License 1.0. You may not use this file
* except in compliance with the Camunda License 1.0.
*/
export const c8_8 =
HEADER
cat /tmp/c8-spec.json >> src/openapi/camunda8/c8_8.js
```

### 4. Format the file

Run Prettier to produce a consistent, diff-friendly format. Prettier
automatically removes unnecessary quotes from object keys and applies the
project's tab-based indentation:

```bash
npm run format:spec
```

### 5. Review the diff

Use the diff script to get a summary of what changed:

```bash
node diff-c8-specs.mjs /tmp/old_c8_8.js src/openapi/camunda8/c8_8.js
```

Save it to a file for reference:

```bash
node diff-c8-specs.mjs /tmp/old_c8_8.js src/openapi/camunda8/c8_8.js --output /tmp/spec-diff.md
```

The output includes:

- **Stats** — tag/path/operation/schema counts and the numbers to put in
  `mapping_7_23_to_8_8.jsx`
- **Added/removed endpoints** — check if new endpoints need mappings
- **Added/removed tags** — new endpoint groups
- **Changed endpoints** — request body property changes on existing endpoints
- **Added/removed schemas** — new or deleted data types
- **Broken mapping targets** — mappings that reference C8 paths no longer in
  the spec

### 6. Update the hardcoded stats

The diff output includes a line like:

> **Update mapping_7_23_to_8_8.jsx** — C8 stats should read: **29 endpoint groups and 146 endpoints**.

Edit `src/mappings/mapping_7_23_to_8_8.jsx` to match.

### 7. Update mappings as needed

Review the diff output for:

- **Added endpoints** — Do any of these replace previously-discontinued C7
  mappings? Search for `discontinuedExplanation` and `target: {}` in the
  `mapping_details_7_23_to_8_8/` files to find candidates.
- **Removed endpoints** — Do any mappings reference these as targets? The
  "Broken mapping targets" section flags these automatically.
- **Changed endpoints** — Have request body properties been renamed or removed
  that affect existing `direct.rowInfo` mappings?

### 8. Verify

```bash
npm run build
npm run lint
```

## File format

The `c8_8.js` file is a JavaScript ES module that exports a single OpenAPI
3.0.3 specification object. Running `npm run format:spec` (Prettier) ensures:

- Tab indentation
- Trailing commas
- Unquoted object keys where valid JS identifiers (quoted only when required:
  paths like `"/topology"`, HTTP status codes like `"200"`, media types, `$ref`)
- Consistent line wrapping

This makes `git diff` output clean and reviewable when updating the spec.
