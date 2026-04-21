# Updating the C8 API Mapping

This guide walks through how to update the API mapping when a new Camunda 8
minor version is released (e.g. 8.8 → 8.9). Patch releases generally do not change
the endpoint surface.

## Overview

The update process has three phases:

1. **Replace the spec** — swap in the new OpenAPI YAML
2. **Diff the endpoints** — find what was added, removed, or renamed
3. **Update the mappings** — fix stale targets and fill newly-mappable gaps

## Source of truth

The OpenAPI specification lives in the Camunda monorepo:

```
zeebe/gateway-protocol/src/main/proto/rest-api.yaml
```

When a new Camunda 8 minor version is released, a new mapping is added
(e.g. `mapping_7_23_to_8_9`). Existing mappings for older versions are
generally not updated. Check out the corresponding release tag
(e.g. `8.9.0`) — patch releases do not change the endpoint surface.

## Prerequisites

- Node.js (18+)
- `npx` (bundled with npm)
- A local checkout of the [camunda/camunda](https://github.com/camunda/camunda) repo (or just the YAML file)

Install project dependencies if you haven't already:

```bash
cd code-conversion/api-mapping
npm install
```

---

> **Note:** The examples below use `c8_8` as the version. Replace file names,
> export names, and directory paths with the actual target version
> (e.g. `c8_9`, `mapping_details_7_23_to_8_9`).

## Phase 1: Replace the spec

### 1.1 Convert the YAML spec to JSON

```bash
npx -y js-yaml /path/to/camunda/zeebe/gateway-protocol/src/main/proto/rest-api.yaml > /tmp/c8-spec.json
```

### 1.2 Back up the current spec

```bash
cp src/openapi/camunda8/c8_8.js /tmp/old_c8_8.mjs
```

### 1.3 Create the new c8_8.js

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

### 1.4 Format the file

Run Prettier to produce a consistent, diff-friendly format:

```bash
npm run format:spec
```

---

## Phase 2: Diff the endpoints

### 2.1 Generate endpoint lists

Generate a sorted list of endpoints from the **old** and **new** spec:

```bash
# Old spec (backed up in step 1.2)
node --input-type=module -e "
import('/tmp/old_c8_8.mjs').then(mod => {
  for (const [path, methods] of Object.entries(mod.c8_8.paths)) {
    for (const [method, details] of Object.entries(methods)) {
      if (['get','post','put','delete','patch'].includes(method))
        console.log((details.tags||['untagged']).join(', ') + ' | ' + method.toUpperCase() + ' ' + path);
    }
  }
});
" | sort > /tmp/old_endpoints.txt

# New spec
node --input-type=module -e "
import('$(pwd)/src/openapi/camunda8/c8_8.js').then(mod => {
  for (const [path, methods] of Object.entries(mod.c8_8.paths)) {
    for (const [method, details] of Object.entries(methods)) {
      if (['get','post','put','delete','patch'].includes(method))
        console.log((details.tags||['untagged']).join(', ') + ' | ' + method.toUpperCase() + ' ' + path);
    }
  }
});
" | sort > /tmp/new_endpoints.txt
```

### 2.2 Compare the lists

```bash
diff /tmp/old_endpoints.txt /tmp/new_endpoints.txt
```

Lines marked `<` are **removed**, `>` are **added**. Look for pairs within the
same tag to identify **renames** (e.g. a path parameter changed from
`{batchOperationId}` to `{batchOperationKey}`).

---

## Phase 3: Update the mappings

### 3.1 Update the endpoint count

Edit `src/mappings/mapping_7_23_to_8_8.jsx` — update the "The Camunda 8.x API
has N endpoint groups and M endpoints" text. Count from the new endpoint list:

```bash
wc -l < /tmp/new_endpoints.txt           # total endpoints
cut -d'|' -f1 /tmp/new_endpoints.txt | sort -u | wc -l  # endpoint groups
```

### 3.2 Check removed/renamed endpoints against existing mappings

For each removed or renamed C8 path, check if any mapping file references it
as a target:

```bash
grep -r 'removed-or-old-path' src/mappings/mapping_details_7_23_to_8_8/
```

If a target path was renamed, update the `target.path` in the mapping file.
If the HTTP method changed (e.g. `PUT` to `POST`), update `target.operation`.

### 3.3 Check added endpoints against unmapped C7 endpoints

List all C7 endpoints that currently have no C8 mapping:

```bash
grep -rl 'target: {}' src/mappings/mapping_details_7_23_to_8_8/ --include='*.jsx'
```

To see which C7 endpoints are unmapped per file:

```bash
for f in $(grep -rl 'target: {}' src/mappings/mapping_details_7_23_to_8_8/ --include='*.jsx'); do
  echo "=== $(basename $f) ==="
  grep -B5 'target: {}' "$f" | grep -E 'path:|operation:'
  echo
done
```

For each added C8 endpoint, check if it provides a **1:1 mapping** for any of
these unmapped C7 endpoints. If it does, fill in the `target` with the new C8
path and operation.

> **Note:** The mapping only tracks high-level endpoint-to-endpoint mappings.
> If a C7 endpoint can only be achieved by combining multiple C8 endpoints,
> that is out of scope for this update.

### 3.4 Verify

```bash
npm run build
npm run lint
```

---

## File format

The `c8_8.js` file is a JavaScript ES module that exports a single OpenAPI
3.0.3 specification object. Running `npm run format:spec` (Prettier) ensures:

- Tab indentation
- Trailing commas
- Unquoted object keys where valid JS identifiers (quoted only when required:
  paths like `"/topology"`, HTTP status codes like `"200"`, media types, `$ref`)
- Consistent line wrapping

This makes `git diff` output clean and reviewable when updating the spec.
