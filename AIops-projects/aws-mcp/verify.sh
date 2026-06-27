#!/usr/bin/env bash
# Quick checks before using AWS MCP with Claude Desktop.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="${HOME}/.local/bin:${PATH}"
ENV_FILE="${SCRIPT_DIR}/.env"
AWS_PROFILE="${AWS_PROFILE:-default}"

if [[ -f "${ENV_FILE}" ]]; then
  set -a && source "${ENV_FILE}" && set +a
fi

PASS=0
FAIL=0

check() {
  local label="$1"
  shift
  if "$@"; then
    echo "  OK  ${label}"
    PASS=$((PASS + 1))
  else
    echo "  FAIL ${label}"
    FAIL=$((FAIL + 1))
  fi
}

echo "AWS MCP verification"
echo "===================="
echo

check "uvx installed" command -v uvx
check "python3 installed" command -v python3
check "aws CLI installed" command -v aws
check "AWS credentials" aws sts get-caller-identity --profile "${AWS_PROFILE}" >/dev/null 2>&1 || aws sts get-caller-identity >/dev/null 2>&1

CLAUDE_CONFIG="${HOME}/Library/Application Support/Claude/claude_desktop_config.json"
check "Claude Desktop config exists" test -f "${CLAUDE_CONFIG}"

if [[ -f "${CLAUDE_CONFIG}" ]]; then
  check "aws-api in Claude config" python3 -c "
import json, sys
d = json.load(open('${CLAUDE_CONFIG}'))
sys.exit(0 if 'aws-api' in d.get('mcpServers', {}) else 1)
"
fi

check "MCP package installed" uv tool list 2>/dev/null | grep -q aws-api-mcp-server || uvx awslabs.aws-api-mcp-server@latest --version >/dev/null 2>&1

echo
echo "Results: ${PASS} passed, ${FAIL} failed"
[[ "${FAIL}" -eq 0 ]]
