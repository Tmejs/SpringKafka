#!/usr/bin/env bash
# Run from any directory; credentials are read from exported environment variables.
set -euo pipefail

usage() {
    cat <<'HELP'
Usage: scripts/demo.sh [all|health|auth|publish|raw-valid|read EVENT_ID|list|invalid-typed|invalid-raw|duplicate]

Requires curl and Python 3. Basic: export APP_BASIC_AUTH_USERNAME and APP_BASIC_AUTH_PASSWORD.
JWT: set AUTH_MODE=jwt and ACCESS_TOKEN (all needs both read/write scopes).
BASE_URL defaults to http://localhost:8080. EVENT_ID and ORDER_ID optionally fix publication IDs.
all runs health, authentication, typed/raw publication, reads, validation and duplicate examples.
invalid-raw only confirms Kafka publication; inspect the DLQ separately (see docs/demo.md).
HELP
}

command=${1:-all}
case "$command" in
    -h|--help|help) usage; exit 0 ;;
    all|health|auth|publish|raw-valid|read|list|invalid-typed|invalid-raw|duplicate) ;;
    *) usage >&2; exit 2 ;;
esac
if [[ "$command" == read && -z "${2:-}" ]]; then
    echo 'Usage: scripts/demo.sh read EVENT_ID' >&2
    exit 2
fi
command -v curl >/dev/null
command -v python3 >/dev/null
BASE_URL=${BASE_URL:-http://localhost:8080}
BASE_URL=${BASE_URL%/}
AUTH_MODE=${AUTH_MODE:-basic}
auth_options=()
case "$AUTH_MODE" in
    basic|jwt) ;;
    *) echo 'AUTH_MODE must be basic or jwt' >&2; exit 2 ;;
esac
if [[ "$command" != health && "$command" != auth ]]; then
    if [[ "$AUTH_MODE" == jwt ]]; then
        : "${ACCESS_TOKEN:?Export ACCESS_TOKEN for JWT requests}"
        auth_options=(--header "Authorization: Bearer $ACCESS_TOKEN")
    else
        : "${APP_BASIC_AUTH_USERNAME:?Export APP_BASIC_AUTH_USERNAME (see docs/demo.md)}"
        : "${APP_BASIC_AUTH_PASSWORD:?Export APP_BASIC_AUTH_PASSWORD (see docs/demo.md)}"
        auth_options=(--user "$APP_BASIC_AUTH_USERNAME:$APP_BASIC_AUTH_PASSWORD")
    fi
fi
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
response_file="$work_dir/response"
api=/api/v1/fund-order-events

show_response() {
    python3 - "$response_file" <<'PY'
import json, pathlib, sys
text = pathlib.Path(sys.argv[1]).read_text()
try:
    print(json.dumps(json.loads(text), indent=2, ensure_ascii=False))
except ValueError:
    print(text or '(empty response)')
PY
}

# Expected errors are checked explicitly rather than hidden by curl --fail.
request() {
    local expected=$1 method=$2 path=$3 body=${4:-} content_type=${5:-application/json}
    local auth=${6:-authenticated} code
    local -a options=(--silent --show-error --connect-timeout 3 --max-time 15
        --request "$method" --output "$response_file" --write-out '%{http_code}')
    if [[ "$auth" == authenticated ]]; then
        options+=("${auth_options[@]}")
    fi
    if [[ "$method" == POST ]]; then
        options+=(--header "Content-Type: $content_type" --data-binary "$body")
    fi
    code=$(curl "${options[@]}" "$BASE_URL$path")
    printf '\n%s %s -> HTTP %s\n' "$method" "$path" "$code"
    show_response
    if [[ "$code" != "$expected" ]]; then
        echo "Expected HTTP $expected, got $code" >&2
        exit 1
    fi
}

new_ids() {
    event_id=${EVENT_ID:-$(python3 -c 'import uuid; print(uuid.uuid4())')}
    order_id=${ORDER_ID:-$(python3 -c 'import uuid; print(uuid.uuid4())')}
}

payload() {
    python3 - "$event_id" "$order_id" "$1" <<'PY'
import datetime, json, sys
print(json.dumps(dict(schemaVersion=1, eventId=sys.argv[1], orderId=sys.argv[2],
    partnerCode='AGENCY_A', status=sys.argv[3],
    occurredAt=datetime.datetime.now(datetime.timezone.utc).isoformat())))
PY
}

wait_for_event() {
    local id=$1 code deadline=$((SECONDS + 30))
    while (( SECONDS < deadline )); do
        code=$(curl --silent --show-error --connect-timeout 3 --max-time 5 \
            "${auth_options[@]}" \
            --output "$response_file" --write-out '%{http_code}' "$BASE_URL$api/$id")
        case "$code" in
            200) printf '\nConsumed event %s -> HTTP 200\n' "$id"; show_response; return ;;
            404) sleep 0.5 ;;
            *) show_response; echo "Read failed: HTTP $code" >&2; exit 1 ;;
        esac
    done
    echo "Event $id was not readable within 30 seconds" >&2
    exit 1
}

health() { request 200 GET /actuator/health/readiness '' '' anonymous; }
auth() { request 401 GET "$api" '' '' anonymous; }
publish() {
    new_ids
    # REJECTED is a valid business outcome and should be stored, not dead-lettered.
    request 202 POST "$api" "$(payload REJECTED)"
    printf '\nEVENT_ID=%s\nORDER_ID=%s\nRead with: scripts/demo.sh read %s\n' "$event_id" "$order_id" "$event_id"
}
raw_valid() {
    new_ids
    request 202 POST "$api/raw" "$(payload EXECUTED)" text/plain
    wait_for_event "$event_id"
}
invalid_typed() {
    new_ids
    # The subsequent 404 check needs an unused ID, even when replay IDs are exported.
    event_id=$(python3 -c 'import uuid; print(uuid.uuid4())')
    request 400 POST "$api" "$(payload UNKNOWN)"
    request 404 GET "$api/$event_id"
}
invalid_raw() {
    local marker
    marker=$(python3 -c 'import uuid; print(uuid.uuid4())')
    request 202 POST "$api/raw" "{not-json-demo-$marker" text/plain
    printf '\nDLQ marker: %s\nHTTP 202 confirms publication only. Look for this marker in the DLQ.\n' "$marker"
}
duplicate() {
    new_ids
    local body
    body=$(payload ACCEPTED)
    request 202 POST "$api" "$body"
    wait_for_event "$event_id"
    request 202 POST "$api" "$body"
    printf '\nReplayed the same eventId: %s. A second 202 acknowledges publication.\n' "$event_id"
    echo 'The integration tests prove both deliveries are consumed without a second row.'
    wait_for_event "$event_id"
}

case "$command" in
    all)
        health
        auth
        publish
        wait_for_event "$event_id"
        raw_valid
        invalid_typed
        duplicate
        invalid_raw
        request 200 GET "$api?page=0&size=5"
        ;;
    health) health ;;
    auth) auth ;;
    publish) publish ;;
    raw-valid) raw_valid ;;
    read) wait_for_event "$2" ;;
    list) request 200 GET "$api?page=0&size=5" ;;
    invalid-typed) invalid_typed ;;
    invalid-raw) invalid_raw ;;
    duplicate) duplicate ;;
esac
