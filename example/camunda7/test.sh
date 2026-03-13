#!/usr/bin/env bash
#
# Smoke test for the Camunda 7 example application.
# Requires: curl, jq
# Usage: ./test.sh [BASE_URL]
#
set -euo pipefail

BASE_URL="${1:-http://localhost:8010}"
ENGINE_REST="$BASE_URL/engine-rest"
PASS=0
FAIL=0

green() { printf "\033[32m%s\033[0m\n" "$*"; }
red()   { printf "\033[31m%s\033[0m\n" "$*"; }
bold()  { printf "\033[1m%s\033[0m\n" "$*"; }

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    green "  PASS: $label"
    PASS=$((PASS + 1))
  else
    red "  FAIL: $label (expected: $expected, got: $actual)"
    FAIL=$((FAIL + 1))
  fi
}

# ---------------------------------------------------------------------------
bold "Waiting for Camunda 7 to be ready..."
# ---------------------------------------------------------------------------
for i in $(seq 1 60); do
  if curl -sf "$ENGINE_REST/engine" > /dev/null 2>&1; then
    green "Camunda 7 is ready."
    break
  fi
  if [ "$i" -eq 60 ]; then
    red "Camunda 7 did not become ready within 60 seconds."
    exit 1
  fi
  sleep 2
done

# ---------------------------------------------------------------------------
bold ""
bold "=== Test 1: Start execution-listener-test via custom controller ==="
# ---------------------------------------------------------------------------

RESPONSE=$(curl -s -X POST "$BASE_URL/test/execution-listener?foo=hello")
echo "  Response: $RESPONSE"

# Extract process instance ID from response text
PI_ID_1=$(echo "$RESPONSE" | grep -oE '[0-9a-f-]{36}' | head -1 || true)

if [ -n "$PI_ID_1" ]; then
  green "  PASS: Process started (id: $PI_ID_1)"
  PASS=$((PASS + 1))

  sleep 1

  # Verify the process completed (no user task, should finish immediately)
  STATE=$(curl -s "$ENGINE_REST/history/process-instance/$PI_ID_1" | jq -r '.state')
  assert_eq "Process completed" "COMPLETED" "$STATE"
else
  red "  FAIL: Could not start process via controller"
  FAIL=$((FAIL + 1))
fi

# ---------------------------------------------------------------------------
bold ""
bold "=== Test 2: Start execution-listener-test via engine REST API ==="
# ---------------------------------------------------------------------------

PI_ID_2=$(curl -s -X POST "$ENGINE_REST/process-definition/key/execution-listener-test/start" \
  -H "Content-Type: application/json" \
  -d '{"variables": {"foo": {"value": "world", "type": "String"}}}' \
  | jq -r '.id')

echo "  Process instance: $PI_ID_2"

if [ "$PI_ID_2" != "null" ] && [ -n "$PI_ID_2" ]; then
  green "  PASS: Process started via REST API"
  PASS=$((PASS + 1))

  sleep 1

  STATE=$(curl -s "$ENGINE_REST/history/process-instance/$PI_ID_2" | jq -r '.state')
  assert_eq "Process completed" "COMPLETED" "$STATE"
else
  red "  FAIL: Could not start process via REST API"
  FAIL=$((FAIL + 1))
fi

# ---------------------------------------------------------------------------
bold ""
bold "=== Test 3: Start my-project-process, complete user task ==="
# ---------------------------------------------------------------------------

PI_ID_3=$(curl -s -X POST "$ENGINE_REST/process-definition/key/my-project-process/start" \
  -H "Content-Type: application/json" \
  -d '{}' \
  | jq -r '.id')

echo "  Process instance: $PI_ID_3"

if [ "$PI_ID_3" != "null" ] && [ -n "$PI_ID_3" ]; then
  green "  PASS: Process started"
  PASS=$((PASS + 1))

  sleep 1

  # Verify there is an active user task
  TASK_ID=$(curl -s "$ENGINE_REST/task?processInstanceId=$PI_ID_3" | jq -r '.[0].id')
  TASK_NAME=$(curl -s "$ENGINE_REST/task?processInstanceId=$PI_ID_3" | jq -r '.[0].name')

  if [ "$TASK_ID" != "null" ] && [ -n "$TASK_ID" ]; then
    green "  PASS: User task found (id: $TASK_ID, name: $TASK_NAME)"
    PASS=$((PASS + 1))

    # Claim the task as demo user
    curl -s -X POST "$ENGINE_REST/task/$TASK_ID/claim" \
      -H "Content-Type: application/json" \
      -d '{"userId": "demo"}' > /dev/null

    # Complete the task
    curl -s -X POST "$ENGINE_REST/task/$TASK_ID/complete" \
      -H "Content-Type: application/json" \
      -d '{}' > /dev/null

    sleep 1

    # Verify the process completed
    STATE=$(curl -s "$ENGINE_REST/history/process-instance/$PI_ID_3" | jq -r '.state')
    assert_eq "Process completed after task completion" "COMPLETED" "$STATE"
  else
    red "  FAIL: No user task found"
    FAIL=$((FAIL + 1))
  fi
else
  red "  FAIL: Could not start my-project-process"
  FAIL=$((FAIL + 1))
fi

# ---------------------------------------------------------------------------
bold ""
bold "=== Summary ==="
# ---------------------------------------------------------------------------
green "Passed: $PASS"
if [ "$FAIL" -gt 0 ]; then
  red "Failed: $FAIL"
  exit 1
else
  green "All tests passed!"
fi
