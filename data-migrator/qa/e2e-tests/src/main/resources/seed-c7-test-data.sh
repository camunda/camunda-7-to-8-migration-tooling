#!/bin/bash
# Seeds Camunda 7 with test process instances required for e2e tests.
# Usage: seed-c7-test-data.sh <C7_REST_URL> <C7_BPMNS_DIR>
# Example: seed-c7-test-data.sh http://camunda7:8080/engine-rest /tmp/c7-bpmns

set -e

C7_REST="${1:-http://camunda7:8080/engine-rest}"
C7_BPMNS_DIR="${2:-/tmp/c7-bpmns}"

# ---------------------------------------------------------------------------
# Wait for C7 REST API to be ready
# ---------------------------------------------------------------------------
echo "Waiting for C7 REST API at ${C7_REST}..."
for i in $(seq 1 30); do
  if curl -sf --connect-timeout 5 --max-time 10 "${C7_REST}/engine" > /dev/null 2>&1; then
    echo "C7 REST API is ready"
    break
  fi
  echo "Attempt ${i}: C7 REST API not ready yet..."
  sleep 3
done

# ---------------------------------------------------------------------------
# Deploy BPMNs
# ---------------------------------------------------------------------------
echo "Deploying BPMNs from ${C7_BPMNS_DIR}/..."
ls -la "${C7_BPMNS_DIR}/"
HTTP_CODE=$(curl -s -o /tmp/deploy-response.json -w "%{http_code}" --max-time 30 \
  -X POST "${C7_REST}/deployment/create" \
  -F "deployment-name=process-example" \
  -F "enable-duplicate-filtering=false" \
  -F "callActivityProcess.bpmn=@${C7_BPMNS_DIR}/callActivityProcess.bpmn" \
  -F "miProcess-subprocess.bpmn=@${C7_BPMNS_DIR}/miProcess-subprocess.bpmn")
echo "Deployment response (HTTP ${HTTP_CODE}): $(cat /tmp/deploy-response.json)"
if [ "${HTTP_CODE}" -lt 200 ] || [ "${HTTP_CODE}" -ge 300 ]; then
  echo "ERROR: BPMN deployment failed with HTTP ${HTTP_CODE}" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Start callingProcessId with typed variables
# ---------------------------------------------------------------------------
echo "Starting callingProcessId..."
HTTP_CODE=$(curl -s -o /tmp/start-response.json -w "%{http_code}" --max-time 30 \
  -X POST "${C7_REST}/process-definition/key/callingProcessId/start" \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "stringVar": {"value": "hello world", "type": "String"},
      "intVar":    {"value": 42,           "type": "Integer"},
      "boolVar":   {"value": true,         "type": "Boolean"},
      "doubleVar": {"value": 3.14,         "type": "Double"},
      "longVar":   {"value": 9999999,      "type": "Long"}
    }
  }')
echo "Start response (HTTP ${HTTP_CODE}): $(cat /tmp/start-response.json)"
if [ "${HTTP_CODE}" -lt 200 ] || [ "${HTTP_CODE}" -ge 300 ]; then
  echo "ERROR: Process start failed with HTTP ${HTTP_CODE}" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Poll & complete user tasks
# (parallel MI, cardinality=3 — all 3 exist at once)
# ---------------------------------------------------------------------------
echo "Waiting for user tasks to appear (C7 job executor handles async-before automatically)..."
COUNTER=1
TASKS_FOUND=false
for i in $(seq 1 30); do
  TASKS=$(curl -sf --max-time 10 "${C7_REST}/task?processDefinitionKey=miProcess" 2>/dev/null) || TASKS="[]"
  TASK_COUNT=$(echo "${TASKS}" | jq 'length' 2>/dev/null || echo "0")
  echo "Tasks found (attempt ${i}): ${TASK_COUNT}"
  if [ "${TASK_COUNT}" -gt "0" ]; then
    TASK_IDS=$(echo "${TASKS}" | jq -r '.[].id' 2>/dev/null)
    for TASK_ID in ${TASK_IDS}; do
      HTTP_CODE=$(curl -s -o /tmp/task-complete-response.json -w "%{http_code}" --max-time 10 \
        -X POST "${C7_REST}/task/${TASK_ID}/complete" \
        -H "Content-Type: application/json" \
        -d "{\"variables\": {\"taskResult\": {\"value\": \"result-${COUNTER}\", \"type\": \"String\"}}}")
      if [ "${HTTP_CODE}" -lt 200 ] || [ "${HTTP_CODE}" -ge 300 ]; then
        echo "ERROR: Task completion failed for ${TASK_ID} with HTTP ${HTTP_CODE}" >&2
        exit 1
      fi
      echo "Completed task ${COUNTER}/3: ${TASK_ID} (HTTP ${HTTP_CODE})"
      COUNTER=$((COUNTER + 1))
    done
    TASKS_FOUND=true
    break
  fi
  sleep 2
done
if [ "${TASKS_FOUND}" = "false" ]; then
  echo "ERROR: No user tasks found after 30 attempts" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Wait for failingTask incident
# (async-before service task with invalid class triggers an incident)
# ---------------------------------------------------------------------------
echo "Waiting for failingTask incident to be created by C7 job executor..."
INCIDENT_FOUND=false
for i in $(seq 1 15); do
  INCIDENTS=$(curl -sf --max-time 10 "${C7_REST}/incident?processDefinitionKey=miProcess" 2>/dev/null) || INCIDENTS="[]"
  INCIDENT_COUNT=$(echo "${INCIDENTS}" | jq 'length' 2>/dev/null || echo "0")
  echo "Incidents found (attempt ${i}): ${INCIDENT_COUNT}"
  if [ "${INCIDENT_COUNT}" -gt "0" ]; then
    INCIDENT_FOUND=true
    echo "Incident created for failingTask"
    break
  fi
  sleep 2
done
if [ "${INCIDENT_FOUND}" = "false" ]; then
  echo "WARNING: No incident found for failingTask — the job executor may not have processed it yet. Continuing anyway."
fi

echo "C7 process instances created successfully."
