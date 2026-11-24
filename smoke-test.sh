#!/usr/bin/env bash
# İstifadə: ./smoke-test.sh [BASE_URL]
BASE="${1:-http://localhost:8080}"
EMAIL="smoke$(date +%s)@test.com"
PASS="supersecret123"
FAIL=0

check() {  # check "ad" gözlənilən_kod faktiki_kod
  if [ "$2" = "$3" ]; then echo "OK    $1 ($3)"; else echo "FAIL  $1 (gözlənilən $2, gələn $3)"; FAIL=1; fi
}

code() { curl -s $CURL_OPTS -o /dev/null -w "%{http_code}" "$@"; }

check "health" 200 "$(code $BASE/actuator/health)"

check "register" 201 "$(code -X POST $BASE/api/auth/register \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"

check "wrong password" 401 "$(code -X POST $BASE/api/auth/login \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"wrongpassword\"}")"

TOKEN=$(curl -s $CURL_OPTS -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
[ -n "$TOKEN" ] && echo "OK    login (token alındı)" || { echo "FAIL  login (token yoxdur)"; FAIL=1; }

check "create without token" 401 "$(code -X POST $BASE/api/links \
  -H 'Content-Type: application/json' -d '{"url":"https://github.com"}')"

RESP=$(curl -s $CURL_OPTS -X POST $BASE/api/links -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"url":"https://github.com"}')
CODE=$(echo "$RESP" | grep -o '"shortCode":"[^"]*' | cut -d'"' -f4)
[ -n "$CODE" ] && echo "OK    create link ($CODE)" || { echo "FAIL  create link: $RESP"; FAIL=1; }

check "redirect" 302 "$(code $BASE/$CODE)"
check "unknown code" 404 "$(code $BASE/doesnotexist)"
check "list my links" 200 "$(code $BASE/api/links -H "Authorization: Bearer $TOKEN")"
HEAD=${TOKEN%%.*}
REST=${TOKEN#*.}
PAYLOAD=${REST%%.*}
SIG=${REST#*.}
NEW=A; [ "${SIG:0:1}" = "A" ] && NEW=B
BAD="$HEAD.$PAYLOAD.$NEW${SIG:1}"
check "tampered token" 401 "$(code $BASE/api/links -H "Authorization: Bearer $BAD")"
check "delete" 204 "$(code -X DELETE $BASE/api/links/$CODE -H "Authorization: Bearer $TOKEN")"
check "redirect after delete" 404 "$(code $BASE/$CODE)"

echo
[ $FAIL -eq 0 ] && echo "Hamısı keçdi" || echo "Xətalar var"
exit $FAIL