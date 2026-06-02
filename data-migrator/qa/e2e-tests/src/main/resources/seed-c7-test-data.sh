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
# Deploy BPMNs + DMNs
# (nullExternalTaskProcess.bpmn and nullStandaloneDmn.dmn seed edge cases for
#  issue #1339 — see NULLABILITY-e2e-scenarios.md)
# ---------------------------------------------------------------------------
echo "Deploying BPMNs/DMNs from ${C7_BPMNS_DIR}/..."
ls -la "${C7_BPMNS_DIR}/"
HTTP_CODE=$(curl -s -o /tmp/deploy-response.json -w "%{http_code}" --max-time 30 \
  -X POST "${C7_REST}/deployment/create" \
  -F "deployment-name=process-example" \
  -F "enable-duplicate-filtering=false" \
  -F "callActivityProcess.bpmn=@${C7_BPMNS_DIR}/callActivityProcess.bpmn" \
  -F "miProcess-subprocess.bpmn=@${C7_BPMNS_DIR}/miProcess-subprocess.bpmn" \
  -F "nullExternalTaskProcess.bpmn=@${C7_BPMNS_DIR}/nullExternalTaskProcess.bpmn" \
  -F "nullStandaloneDmn.dmn=@${C7_BPMNS_DIR}/nullStandaloneDmn.dmn")
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

# ===========================================================================
# Nullability edge cases (issue #1339)
#
# Goal: produce C7 historical data that, once migrated, lands as NULL in the
# C8 RDBMS columns whose entity contracts require non-null. After the migrator
# finishes, log into Operate and consult NULLABILITY-e2e-scenarios.md for what
# to look at.
#
# Covered scenarios:
#   1. JobEntity.worker            — external task is started but never locked
#   2. JobEntity.lastUpdateTime    — already produced by miProcess jobs above;
#                                    no extra seeding required
#   3. JobEntity.elementInstanceKey — already produced by miProcess
#                                    async-before jobs above; no extra seeding
#   4. AuditLogEntity.entityKey (JOB)           — setRetries on miProcess
#                                                  failingTask job
#   5. AuditLogEntity.entityKey (EXTERNAL_TASK) — setPriority on the unlocked
#                                                  external task
#   6. DecisionDefinitionEntity.decisionRequirementsId — deploy
#                                                  nullStandaloneDmn.dmn
#                                                  (covered by deployment step
#                                                  above; no extra seeding)
# ===========================================================================

# ---------------------------------------------------------------------------
# Start nullExternalTaskProcessId  (scenarios #1 and the external task that
# scenario #5 will operate on)
# ---------------------------------------------------------------------------
echo "Starting nullExternalTaskProcessId..."
HTTP_CODE=$(curl -s -o /tmp/null-ext-start.json -w "%{http_code}" --max-time 30 \
  -X POST "${C7_REST}/process-definition/key/nullExternalTaskProcessId/start" \
  -H "Content-Type: application/json" \
  -d '{}')
echo "Start response (HTTP ${HTTP_CODE}): $(cat /tmp/null-ext-start.json)"
if [ "${HTTP_CODE}" -lt 200 ] || [ "${HTTP_CODE}" -ge 300 ]; then
  echo "ERROR: nullExternalTaskProcessId start failed with HTTP ${HTTP_CODE}" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Find the unlocked external task and trigger setPriority on it
#   - Leaving it unlocked produces a JobEntity with worker=null (#1)
#   - setPriority records a UserOperationLogEntry whose entityType=EXTERNAL_TASK
#     but no entityKey, which the migrator passes through as null (#5)
# ---------------------------------------------------------------------------
echo "Locating unlocked external task on nullabilityTopic..."
EXT_TASK_FOUND=false
for i in $(seq 1 15); do
  EXT_TASKS=$(curl -sf --max-time 10 "${C7_REST}/external-task?topicName=nullabilityTopic" 2>/dev/null) || EXT_TASKS="[]"
  EXT_TASK_COUNT=$(echo "${EXT_TASKS}" | jq 'length' 2>/dev/null || echo "0")
  echo "External tasks found (attempt ${i}): ${EXT_TASK_COUNT}"
  if [ "${EXT_TASK_COUNT}" -gt "0" ]; then
    EXT_TASK_ID=$(echo "${EXT_TASKS}" | jq -r '.[0].id')
    echo "Using external task ${EXT_TASK_ID} (left unlocked → JobEntity.worker stays null)"
    # HTTP Basic auth is required for C7 to record a UserOperationLogEntry —
    # anonymous REST callers get the operation performed but no audit row, so
    # scenario #5 (AuditLogEntity.entityKey null) would never reach C8.
    HTTP_CODE=$(curl -s -o /tmp/ext-priority.json -w "%{http_code}" --max-time 10 \
      -u demo:demo \
      -X PUT "${C7_REST}/external-task/${EXT_TASK_ID}/priority" \
      -H "Content-Type: application/json" \
      -d '{"priority": 42}')
    echo "setPriority response (HTTP ${HTTP_CODE}): $(cat /tmp/ext-priority.json)"
    if [ "${HTTP_CODE}" -lt 200 ] || [ "${HTTP_CODE}" -ge 300 ]; then
      echo "WARNING: setPriority failed with HTTP ${HTTP_CODE} — audit log for #5 may be missing"
    fi
    EXT_TASK_FOUND=true
    break
  fi
  sleep 2
done
if [ "${EXT_TASK_FOUND}" = "false" ]; then
  echo "WARNING: No external task on nullabilityTopic — scenarios #1 and #5 may be missing"
fi

# ---------------------------------------------------------------------------
# setRetries on the failingTask job from miProcess (scenario #4)
#   - The failingTask incident already has a stuck job from the earlier seed step
#   - setRetries records a UserOperationLogEntry with entityType=JOB. The
#     migrator's AuditLogMigrator sets jobKey on the C8 row but leaves
#     entityKey null → contract violation when read back
# ---------------------------------------------------------------------------
echo "Locating failingTask job for setRetries..."
JOB_FOUND=false
for i in $(seq 1 15); do
  JOBS=$(curl -sf --max-time 10 "${C7_REST}/job?processDefinitionKey=miProcess&withException=true" 2>/dev/null) || JOBS="[]"
  JOB_COUNT=$(echo "${JOBS}" | jq 'length' 2>/dev/null || echo "0")
  echo "Failing jobs found (attempt ${i}): ${JOB_COUNT}"
  if [ "${JOB_COUNT}" -gt "0" ]; then
    JOB_ID=$(echo "${JOBS}" | jq -r '.[0].id')
    echo "Using job ${JOB_ID}"
    # Auth required to record the UserOperationLogEntry — see comment on setPriority above.
    HTTP_CODE=$(curl -s -o /tmp/job-retries.json -w "%{http_code}" --max-time 10 \
      -u demo:demo \
      -X PUT "${C7_REST}/job/${JOB_ID}/retries" \
      -H "Content-Type: application/json" \
      -d '{"retries": 5}')
    echo "setRetries response (HTTP ${HTTP_CODE}): $(cat /tmp/job-retries.json)"
    if [ "${HTTP_CODE}" -lt 200 ] || [ "${HTTP_CODE}" -ge 300 ]; then
      echo "WARNING: setRetries failed with HTTP ${HTTP_CODE} — audit log for #4 may be missing"
    fi
    JOB_FOUND=true
    break
  fi
  sleep 2
done
if [ "${JOB_FOUND}" = "false" ]; then
  echo "WARNING: No failing job found — scenario #4 may be missing"
fi

echo "C7 process instances created successfully."
