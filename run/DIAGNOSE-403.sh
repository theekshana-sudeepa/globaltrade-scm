#!/usr/bin/env bash
# Probes the deployed API directly so we can see exactly which endpoints
# return what, and what roles the issued token actually carries.
BASE="https://localhost:8443/globaltrade/api"
USER_EMAIL="${1:-admin@globaltrade.lk}"
USER_PASS="${2:-Passw0rd!2026}"

echo "==> 1. Logging in as $USER_EMAIL"
LOGIN=$(curl -sk -w '\n%{http_code}' -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_EMAIL\",\"password\":\"$USER_PASS\"}")
CODE=$(echo "$LOGIN" | tail -1)
BODY=$(echo "$LOGIN" | sed '$d')
echo "    HTTP $CODE"
if [ "$CODE" != "200" ]; then echo "$BODY"; exit 1; fi

TOKEN=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
echo "    token length: ${#TOKEN}"

echo
echo "==> 2. What the token actually claims"
echo "$TOKEN" | cut -d. -f2 | python3 -c "
import sys, base64, json
s = sys.stdin.read().strip().replace('-','+').replace('_','/')
s += '=' * (-len(s) % 4)
print(json.dumps(json.loads(base64.b64decode(s)), indent=2))
"

echo
echo "==> 3. Calling each protected endpoint"
probe() {
  local path="$1" need="$2"
  local out code body
  out=$(curl -sk -w '\n%{http_code}' -H "Authorization: Bearer $TOKEN" "$BASE$path")
  code=$(echo "$out" | tail -1)
  body=$(echo "$out" | sed '$d' | head -c 220)
  printf '    %-34s requires %-28s -> HTTP %s\n' "$path" "$need" "$code"
  [ "$code" != "200" ] && echo "        $body"
  return 0
}
probe "/shipments/in-transit"        "ADMIN or LOGISTICS_COORDINATOR"
probe "/shipments/overdue"           "ADMIN or LOGISTICS_COORDINATOR"
probe "/admin/monitoring/metrics"    "ADMIN or LOGISTICS_COORDINATOR"
probe "/admin/monitoring/timers"     "ADMIN"

echo
echo "==> 4. Does the database actually have the seed rows?"
echo "    (if this says 0 shipments, the 403 is hiding an empty database)"
