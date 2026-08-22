#!/bin/bash
# ParanoidX PHASE2: Verify + Test + Report
# Run as regular user (tomas), no sudo needed.
# Usage: bash scripts/phase2-verify-and-report.sh
#
# This script:
# 1. Verifies node + bot bridge are healthy
# 2. Runs test-royal.sh harness
# 3. Generates a status report
# 4. Sends report to torquemada

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$(dirname "$SCRIPT_DIR")"
DATA_DIR="${DATA_DIR:-$HOME/.local/share/ParanoidX}"
REPORT_FILE="/tmp/ParanoidX-phase2-report-$(date +%Y%m%d-%H%M%S).md"
SEND_SCRIPT="$SCRIPT_DIR/send-to-torquemada.sh"

echo "=== ParanoidX PHASE2: Verify + Test + Report ==="
echo "Data: $DATA_DIR"
echo "Src:   $SRC_DIR"

# 1. Verify node API
echo ""
echo "[1/5] Checking node API..."
if curl -s http://127.0.0.1:8080/api/status >/dev/null 2>&1; then
  STATUS=$(curl -s http://127.0.0.1:8080/api/status | python3 -m json.tool)
  echo "Node API: UP"
  echo "$STATUS" | grep -E "status|is_royal|uptime" | sed 's/^/  /'
else
  echo "Node API: DOWN"
  STATUS="{\"status\":\"down\"}"
fi

# 2. Verify bot bridge
echo ""
echo "[2/5] Checking bot bridge..."
if pgrep -f "simplex-chat-island" >/dev/null 2>&1; then
  CLI_PID=$(pgrep -f "simplex-chat-island" | head -1)
  echo "CLI process: RUNNING (pid $CLI_PID)"
  CLI_STATUS="running"
else
  echo "CLI process: NOT RUNNING"
  CLI_STATUS="down"
fi

if ss -tnp | grep -q ':5230 '; then
  echo "WS port 5230: LISTENING"
  WS_STATUS="listening"
else
  echo "WS port 5230: CLOSED"
  WS_STATUS="closed"
fi

# Check contact file
if [ -f "$DATA_DIR/island_services_contact.txt" ]; then
  CONTACT_LINK=$(grep -o 'simplex:/contact#[^ ]*' "$DATA_DIR/island_services_contact.txt" | head -1 || echo "")
  if [ -n "$CONTACT_LINK" ]; then
    echo "Contact link: REAL ($(echo "$CONTACT_LINK" | cut -c1-60)...)"
    CONTACT_STATUS="real"
  else
    echo "Contact link: MISSING/PLACEHOLDER"
    CONTACT_STATUS="placeholder"
  fi
else
  echo "Contact file: NOT FOUND"
  CONTACT_STATUS="missing"
fi

# Check bridge logs
if [ -f "$DATA_DIR/logs/dashboard.log" ]; then
  BRIDGE_LOG_LINES=$(wc -l < "$DATA_DIR/logs/dashboard.log")
  LAST_BRIDGE=$(tail -1 "$DATA_DIR/logs/dashboard.log" 2>/dev/null || echo "")
  echo "Bridge log: $BRIDGE_LOG_LINES lines"
  echo "  Last: $(echo "$LAST_BRIDGE" | cut -c1-100)"
else
  echo "Bridge log: NOT FOUND"
  BRIDGE_LOG_LINES=0
fi

# 3. Run test-royal.sh if available
echo ""
echo "[3/5] Running test-royal.sh harness..."
if [ -x "$SRC_DIR/scripts/test-royal.sh" ]; then
  TEST_OUTPUT=$("$SRC_DIR/scripts/test-royal.sh" 2>&1 | tail -30) || true
  echo "$TEST_OUTPUT"
  TEST_STATUS="executed"
else
  echo "test-royal.sh not found or not executable"
  TEST_STATUS="skipped"
fi

# 4. Check for Go tests
echo ""
echo "[4/5] Checking Go unit tests..."
if find "$SRC_DIR" -name "*_test.go" | grep -q .; then
  TEST_FILES=$(find "$SRC_DIR" -name "*_test.go" | wc -l)
  echo "Go test files: $TEST_FILES"
  TEST_GO_STATUS="present"
else
  echo "Go test files: 0 (none found)"
  TEST_GO_STATUS="absent"
fi

# 5. Generate report
echo ""
echo "[5/5] Generating report..."
cat > "$REPORT_FILE" <<EOF
# ParanoidX PHASE2 REPORT
Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')
By: phase2-verify-and-report.sh

## 1. NODE STATUS
- API: $(echo "$STATUS" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("status","unknown"))' 2>/dev/null || echo "unknown")
- Royal: $(echo "$STATUS" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("is_royal","unknown"))' 2>/dev/null || echo "unknown")
- Uptime: $(echo "$STATUS" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("uptime_seconds","?"))' 2>/dev/null || echo "?")s

## 2. BOT BRIDGE STATUS
- CLI process: $CLI_STATUS
- WS port 5230: $WS_STATUS
- Contact link: $CONTACT_STATUS
- Bridge log lines: $BRIDGE_LOG_LINES

## 3. TEST RESULTS
- test-royal.sh: $TEST_STATUS
- Go unit tests: $TEST_GO_STATUS

## 4. NEXT STEPS
- If CLI is down or WS is closed: run \`sudo bash scripts/sudo-wrapper.sh full-restart\`
- If contact is placeholder: run CLI interactively once to generate real link
- If tests absent: add unit tests for economy/server packages
- Proceed with stub implementation (handleStatus, handleAddresses, etc.)

## 5. FULL NODE STATUS JSON
\`\`\`
$STATUS
\`\`\`
EOF

echo "Report saved: $REPORT_FILE"

# 6. Send to torquemada if script exists
echo ""
echo "Sending report to torquemada..."
if [ -x "$SEND_SCRIPT" ]; then
  bash "$SEND_SCRIPT" "$(cat "$REPORT_FILE")" || echo "Send failed (check token/chat config)"
else
  echo "send-to-torquemada.sh not found, skipping Telegram delivery"
fi

echo ""
echo "=== PHASE2 COMPLETE ==="
echo "Report: $REPORT_FILE"
