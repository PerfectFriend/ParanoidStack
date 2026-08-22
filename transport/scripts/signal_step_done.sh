#!/bin/bash
# Signal to bot that a step is completed, waiting for user in main console.
# Usage: ./signal_step_done.sh "краткое описание шага" "краткий результат"
# Formats exactly as requested and sends via send-to-torquemada.sh

set -euo pipefail

source "$(dirname "$0")/royal-common.sh" 2>/dev/null || true
: "${SEND_TO:=$(dirname "$0")/send-to-torquemada.sh}"

STEP_DESC="${1:-неизвестный шаг}"
RESULT="${2:-выполнено}"

MSG="шаг (${STEP_DESC} - ${RESULT}) завершен. жду тебя в главной консоли."

"$SEND_TO" "$MSG"

echo "Signaled to bot: $MSG"
