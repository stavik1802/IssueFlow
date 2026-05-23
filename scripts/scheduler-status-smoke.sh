#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCHEDULER_DELAY="${SCHEDULER_DELAY:-PT15M}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-3900}"
POLL_SECONDS="${POLL_SECONDS:-60}"
TRACE_FILE="${TRACE_FILE:-target/scheduler-status-smoke/trace.jsonl}"
APP_LOG="${APP_LOG:-target/scheduler-status-smoke/spring-boot.log}"
PYTHON_BIN="${PYTHON_BIN:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$(dirname "$TRACE_FILE")" "$(dirname "$APP_LOG")"
: > "$TRACE_FILE"
: > "$APP_LOG"

SUFFIX="$(date +%s)-$RANDOM"
ADMIN_USERNAME="admin-smoke-$SUFFIX"
DEV_USERNAME="dev-smoke-$SUFFIX"
PASSWORD="secret"
ADMIN_TOKEN=""
APP_PID=""

if [[ -z "$PYTHON_BIN" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  else
    PYTHON_BIN="python"
  fi
fi

cleanup() {
  if [[ -n "${APP_PID:-}" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
    echo "Stopping Spring Boot app pid $APP_PID"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

json_get() {
  local file="$1"
  local expr="$2"
  "$PYTHON_BIN" - "$file" "$expr" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    data = json.load(handle)

value = data
for part in sys.argv[2].split("."):
    if not part:
        continue
    value = value[int(part)] if isinstance(value, list) else value[part]

if value is None:
    print("")
elif isinstance(value, bool):
    print(str(value).lower())
else:
    print(value)
PY
}

record_call() {
  local name="$1"
  local method="$2"
  local path="$3"
  local request_body="$4"
  local status="$5"
  local response_body_file="$6"

  "$PYTHON_BIN" - "$TRACE_FILE" "$name" "$method" "$path" "$request_body" "$status" "$response_body_file" <<'PY'
import json
import sys
from datetime import datetime, timezone

trace_file, name, method, path, request_body, status, response_file = sys.argv[1:]

def decode(text):
    if text == "":
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text

with open(response_file, encoding="utf-8") as handle:
    response_text = handle.read()

entry = {
    "timestamp": datetime.now(timezone.utc).isoformat(),
    "name": name,
    "request": {
        "method": method,
        "path": path,
        "body": decode(request_body),
    },
    "response": {
        "status": int(status),
        "body": decode(response_text),
    },
}
with open(trace_file, "a", encoding="utf-8") as handle:
    handle.write(json.dumps(entry, ensure_ascii=False) + "\n")
PY
}

api() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local token="${5:-}"
  local expected="${6:-200}"
  local response_file
  response_file="$(mktemp)"

  local headers=(-H "Accept: application/json")
  if [[ -n "$body" ]]; then
    headers+=(-H "Content-Type: application/json")
  fi
  if [[ -n "$token" ]]; then
    headers+=(-H "Authorization: Bearer $token")
  fi

  local status
  if [[ -n "$body" ]]; then
    status="$(curl -sS -o "$response_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" "${headers[@]}" -d "$body")"
  else
    status="$(curl -sS -o "$response_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" "${headers[@]}")"
  fi

  record_call "$name" "$method" "$path" "$body" "$status" "$response_file"

  if [[ "$status" != "$expected" ]]; then
    echo "FAIL: $name expected HTTP $expected but got $status"
    echo "Response:"
    cat "$response_file"
    echo
    echo "Trace: $TRACE_FILE"
    exit 1
  fi

  cat "$response_file"
  rm -f "$response_file"
}

wait_for_app() {
  echo "Waiting for Spring Boot at $BASE_URL ..."
  for _ in $(seq 1 120); do
    if curl -sS -o /dev/null "$BASE_URL/users" >/dev/null 2>&1; then
      echo "Spring Boot is responding."
      return
    fi
    sleep 2
  done
  echo "FAIL: app did not respond in time. See $APP_LOG"
  exit 1
}

expect_field() {
  local file="$1"
  local field="$2"
  local expected="$3"
  local actual
  actual="$(json_get "$file" "$field")"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: expected $field=$expected but got $actual"
    echo "Body:"
    cat "$file"
    echo
    echo "Trace: $TRACE_FILE"
    exit 1
  fi
}

echo "Starting PostgreSQL with docker compose ..."
docker compose up -d

echo "Starting Spring Boot with scheduler delay $SCHEDULER_DELAY ..."
if [[ ! -x ./mvnw ]]; then
  chmod +x ./mvnw
fi
ISSUEFLOW_BOOTSTRAP_ADMIN_ENABLED=false \
ISSUEFLOW_AUTH_PASSWORD="$PASSWORD" \
ISSUEFLOW_SCHEDULER_TICKET_ESCALATION_DELAY="$SCHEDULER_DELAY" \
./mvnw spring-boot:run > "$APP_LOG" 2>&1 &
APP_PID="$!"

wait_for_app

echo "Creating users, project, and tickets ..."
ADMIN_BODY="{\"username\":\"$ADMIN_USERNAME\",\"email\":\"$ADMIN_USERNAME@example.com\",\"fullName\":\"Smoke Admin\",\"role\":\"ADMIN\"}"
ADMIN_RESPONSE="$(mktemp)"
api "create admin user" "POST" "/users" "$ADMIN_BODY" "" "200" > "$ADMIN_RESPONSE"
ADMIN_ID="$(json_get "$ADMIN_RESPONSE" "id")"

LOGIN_BODY="{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$PASSWORD\"}"
LOGIN_RESPONSE="$(mktemp)"
api "login admin" "POST" "/auth/login" "$LOGIN_BODY" "" "200" > "$LOGIN_RESPONSE"
ADMIN_TOKEN="$(json_get "$LOGIN_RESPONSE" "accessToken")"

DEV_BODY="{\"username\":\"$DEV_USERNAME\",\"email\":\"$DEV_USERNAME@example.com\",\"fullName\":\"Smoke Developer\",\"role\":\"DEVELOPER\"}"
DEV_RESPONSE="$(mktemp)"
api "create developer user" "POST" "/users" "$DEV_BODY" "$ADMIN_TOKEN" "200" > "$DEV_RESPONSE"
DEV_ID="$(json_get "$DEV_RESPONSE" "id")"

PROJECT_BODY="{\"name\":\"Smoke Scheduler Project $SUFFIX\",\"description\":\"Created by scheduler/status smoke script\",\"ownerId\":$ADMIN_ID}"
PROJECT_RESPONSE="$(mktemp)"
api "create project" "POST" "/projects" "$PROJECT_BODY" "$ADMIN_TOKEN" "200" > "$PROJECT_RESPONSE"
PROJECT_ID="$(json_get "$PROJECT_RESPONSE" "id")"

STATUS_TICKET_BODY="{\"projectId\":$PROJECT_ID,\"assigneeId\":$DEV_ID,\"title\":\"Status transition smoke\",\"description\":\"Verify legal status changes and DONE immutability\",\"status\":\"TODO\",\"priority\":\"LOW\",\"type\":\"BUG\"}"
STATUS_TICKET_RESPONSE="$(mktemp)"
api "create status transition ticket" "POST" "/tickets" "$STATUS_TICKET_BODY" "$ADMIN_TOKEN" "200" > "$STATUS_TICKET_RESPONSE"
STATUS_TICKET_ID="$(json_get "$STATUS_TICKET_RESPONSE" "id")"

api "status TODO to IN_PROGRESS" "PATCH" "/tickets/$STATUS_TICKET_ID" '{"status":"IN_PROGRESS"}' "$ADMIN_TOKEN" "200" >/dev/null
api "status IN_PROGRESS to IN_REVIEW" "PATCH" "/tickets/$STATUS_TICKET_ID" '{"status":"IN_REVIEW"}' "$ADMIN_TOKEN" "200" >/dev/null
api "status IN_REVIEW to DONE" "PATCH" "/tickets/$STATUS_TICKET_ID" '{"status":"DONE"}' "$ADMIN_TOKEN" "200" >/dev/null
api "update DONE ticket must fail" "PATCH" "/tickets/$STATUS_TICKET_ID" '{"title":"should fail"}' "$ADMIN_TOKEN" "422" >/dev/null

DUE_DATE="$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)"
ESCALATION_TICKET_BODY="{\"projectId\":$PROJECT_ID,\"assigneeId\":$DEV_ID,\"title\":\"Scheduler escalation smoke\",\"description\":\"Wait for real scheduler cycles\",\"status\":\"TODO\",\"priority\":\"LOW\",\"type\":\"FEATURE\",\"dueDate\":\"$DUE_DATE\"}"
ESCALATION_TICKET_RESPONSE="$(mktemp)"
api "create overdue low priority ticket" "POST" "/tickets" "$ESCALATION_TICKET_BODY" "$ADMIN_TOKEN" "200" > "$ESCALATION_TICKET_RESPONSE"
ESCALATION_TICKET_ID="$(json_get "$ESCALATION_TICKET_RESPONSE" "id")"

echo "Waiting up to $MAX_WAIT_SECONDS seconds for scheduler to promote LOW -> MEDIUM -> HIGH -> CRITICAL."
echo "Polling every $POLL_SECONDS seconds. Trace is written to $TRACE_FILE"

deadline=$((SECONDS + MAX_WAIT_SECONDS))
seen_medium=0
seen_high=0
seen_critical=0

while (( SECONDS < deadline )); do
  CURRENT_RESPONSE="$(mktemp)"
  api "poll escalation ticket" "GET" "/tickets/$ESCALATION_TICKET_ID" "" "$ADMIN_TOKEN" "200" > "$CURRENT_RESPONSE"
  priority="$(json_get "$CURRENT_RESPONSE" "priority")"
  status="$(json_get "$CURRENT_RESPONSE" "status")"
  overdue="$(json_get "$CURRENT_RESPONSE" "isOverdue")"

  echo "Ticket $ESCALATION_TICKET_ID priority=$priority status=$status isOverdue=$overdue elapsed=${SECONDS}s"

  if [[ "$status" != "TODO" ]]; then
    echo "FAIL: scheduler changed status to $status, but it must only change priority/isOverdue."
    exit 1
  fi
  [[ "$priority" == "MEDIUM" ]] && seen_medium=1
  [[ "$priority" == "HIGH" ]] && seen_high=1
  [[ "$priority" == "CRITICAL" ]] && seen_critical=1

  if [[ "$priority" == "CRITICAL" ]]; then
    expect_field "$CURRENT_RESPONSE" "isOverdue" "true"
    break
  fi

  rm -f "$CURRENT_RESPONSE"
  sleep "$POLL_SECONDS"
done

FINAL_RESPONSE="$(mktemp)"
api "final escalation ticket read" "GET" "/tickets/$ESCALATION_TICKET_ID" "" "$ADMIN_TOKEN" "200" > "$FINAL_RESPONSE"
expect_field "$FINAL_RESPONSE" "priority" "CRITICAL"
expect_field "$FINAL_RESPONSE" "status" "TODO"
expect_field "$FINAL_RESPONSE" "isOverdue" "true"

if [[ "$seen_medium" != "1" || "$seen_high" != "1" || "$seen_critical" != "1" ]]; then
  echo "FAIL: did not observe all escalation stages. medium=$seen_medium high=$seen_high critical=$seen_critical"
  echo "Use a longer MAX_WAIT_SECONDS or a shorter SCHEDULER_DELAY."
  exit 1
fi

api "audit log contains auto escalation" "GET" "/audit-logs?action=AUTO_ESCALATE&actor=SYSTEM&size=20" "" "$ADMIN_TOKEN" "200" >/dev/null

echo "PASS: status transitions and scheduled escalation worked."
echo "Trace: $TRACE_FILE"
echo "Spring log: $APP_LOG"
