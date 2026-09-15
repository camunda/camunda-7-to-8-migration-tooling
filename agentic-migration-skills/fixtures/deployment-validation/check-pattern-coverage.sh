#!/bin/sh
set -eu

fixture_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
inventory="$fixture_dir/expected-deployment-inventory.tsv"
patterns="$fixture_dir/expected-deployment-patterns.txt"

test "$(awk -F '	' '$1 == "BPMN" { count++ } END { print count + 0 }' "$inventory")" -eq 1
test "$(awk -F '	' '$1 == "DMN" { count++ } END { print count + 0 }' "$inventory")" -eq 0
grep -Fqx 'classpath*:/converted-c8-deployment-validation.bpmn' "$patterns"
if grep -Fq '.dmn' "$patterns"; then
  printf '%s\n' 'unexpected DMN deployment pattern' >&2
  exit 1
fi

printf '%s\n' 'deployment pattern coverage passed'
