#!/bin/sh

set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
INCLUDES='org.apache.httpcomponents.client5:httpclient5,org.apache.httpcomponents.core5:httpcore5,org.apache.httpcomponents.core5:httpcore5-h2'

dependency_tree() {
  mvn -f "$ROOT/$1/pom.xml" dependency:tree -Dverbose -Dincludes="$INCLUDES"
}

assert_contains() {
  name=$1
  label=$2
  text=$3
  expected=$4

  printf '%s\n' "$text" | grep -F "$expected" >/dev/null ||
    {
      printf 'Expected %s (%s) to contain %s\n' "$name" "$label" "$expected" >&2
      exit 1
    }
}

broken_tree=$(dependency_tree broken)
assert_contains broken httpclient "$broken_tree" 'httpclient5:jar:5.6.3'
assert_contains broken httpcore "$broken_tree" 'httpcore5:jar:5.2.5'
assert_contains broken httpcore-h2 "$broken_tree" 'httpcore5-h2:jar:5.2.5'

fixed_tree=$(dependency_tree fixed)
assert_contains fixed httpclient "$fixed_tree" 'httpclient5:jar:5.6.3'
assert_contains fixed httpcore "$fixed_tree" 'httpcore5:jar:5.4.3'
assert_contains fixed httpcore-h2 "$fixed_tree" 'httpcore5-h2:jar:5.4.3'
mvn -q -f "$ROOT/fixed/pom.xml" test

mvn -q -f "$ROOT/control-bom/pom.xml" install
control_tree=$(dependency_tree control)
assert_contains control httpclient "$control_tree" 'httpclient5:jar:5.6.3'
assert_contains control httpcore "$control_tree" 'httpcore5:jar:5.4.3'
assert_contains control httpcore-h2 "$control_tree" 'httpcore5-h2:jar:5.4.3'
mvn -q -f "$ROOT/control/pom.xml" test

printf '%s\n' 'HttpClient compatibility fixture passed.'
