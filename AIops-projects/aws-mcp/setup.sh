#!/usr/bin/env bash
# Install prerequisites and merge AWS MCP server into Claude Desktop config.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="${HOME}/.local/bin:${PATH}"
ENV_FILE="${SCRIPT_DIR}/.env"
EXAMPLE_CONFIG="${SCRIPT_DIR}/claude_desktop_config.example.json"

AWS_PROFILE="${AWS_PROFILE:-default}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
READ_OPERATIONS_ONLY="${READ_OPERATIONS_ONLY:-false}"
REQUIRE_MUTATION_CONSENT="${REQUIRE_MUTATION_CONSENT:-false}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a && source "${ENV_FILE}" && set +a
fi

if [[ "$(uname -s)" == "Darwin" ]]; then
  CLAUDE_CONFIG="${HOME}/Library/Application Support/Claude/claude_desktop_config.json"
else
  echo "This script targets macOS (Claude Desktop path). Edit claude_desktop_config.example.json manually on other OSes."
  CLAUDE_CONFIG="${HOME}/.config/Claude/claude_desktop_config.json"
fi

echo "==> AWS MCP setup (official awslabs.aws-api-mcp-server)"
echo

# --- uv (recommended launcher) ---
if ! command -v uvx >/dev/null 2>&1; then
  echo "==> Installing uv (provides uvx for MCP server)..."
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="${HOME}/.local/bin:${PATH}"
fi

if ! command -v uvx >/dev/null 2>&1; then
  echo "ERROR: uvx not found after install. Add ~/.local/bin to PATH and re-run."
  exit 1
fi

# --- Python ---
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 is required (3.10+). Install via Homebrew: brew install python"
  exit 1
fi

PY_VERSION="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
PY_MAJOR="${PY_VERSION%%.*}"
PY_MINOR="${PY_VERSION#*.}"
if [[ "${PY_MAJOR}" -lt 3 ]] || { [[ "${PY_MAJOR}" -eq 3 ]] && [[ "${PY_MINOR}" -lt 10 ]]; }; then
  echo "ERROR: Python 3.10+ required (found ${PY_VERSION})"
  exit 1
fi

# --- AWS CLI + credentials ---
if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR: AWS CLI not found. Install: brew install awscli"
  exit 1
fi

echo "==> Checking AWS credentials (profile: ${AWS_PROFILE})..."
CREDS_OK=false
if aws sts get-caller-identity --profile "${AWS_PROFILE}" >/dev/null 2>&1; then
  CREDS_OK=true
elif aws sts get-caller-identity >/dev/null 2>&1; then
  AWS_PROFILE="default"
  CREDS_OK=true
fi

if [[ "${CREDS_OK}" == "true" ]]; then
  CALLER="$(aws sts get-caller-identity --profile "${AWS_PROFILE}" 2>/dev/null || aws sts get-caller-identity)"
  echo "    Account: $(echo "${CALLER}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Account'])")"
  echo "    Arn:     $(echo "${CALLER}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Arn'])")"
else
  echo "    WARN: AWS credentials not configured yet — Claude config will still be written."
  echo "    Run: aws configure --profile ${AWS_PROFILE}"
  echo "    Or:  aws sso login --profile ${AWS_PROFILE}"
fi
echo

# --- Warm the MCP package (install only; do not start stdio server) ---
echo "==> Installing awslabs.aws-api-mcp-server package..."
uv tool install awslabs.aws-api-mcp-server >/dev/null 2>&1 || uvx awslabs.aws-api-mcp-server@latest --version >/dev/null 2>&1 || true
echo "    Package available via uvx"
echo

# --- Merge Claude Desktop config ---
mkdir -p "$(dirname "${CLAUDE_CONFIG}")"

AWS_MCP_BLOCK="$(python3 - "${AWS_PROFILE}" "${AWS_REGION}" "${READ_OPERATIONS_ONLY}" "${REQUIRE_MUTATION_CONSENT}" <<'PY'
import json, sys
profile, region, read_only, consent = sys.argv[1:5]
block = {
    "aws-api": {
        "command": "uvx",
        "args": ["awslabs.aws-api-mcp-server@latest"],
        "env": {
            "AWS_REGION": region,
            "AWS_API_MCP_PROFILE_NAME": profile,
            "READ_OPERATIONS_ONLY": read_only.lower(),
            "REQUIRE_MUTATION_CONSENT": consent.lower(),
            "FASTMCP_LOG_LEVEL": "ERROR",
        },
        "disabled": False,
        "autoApprove": [],
    }
}
print(json.dumps(block["aws-api"], indent=2))
PY
)"

python3 - "${CLAUDE_CONFIG}" "${AWS_MCP_BLOCK}" <<'PY'
import json, pathlib, sys

config_path = pathlib.Path(sys.argv[1])
aws_api = json.loads(sys.argv[2])

if config_path.exists():
    data = json.loads(config_path.read_text())
else:
    data = {}

servers = data.setdefault("mcpServers", {})
servers["aws-api"] = aws_api

config_path.write_text(json.dumps(data, indent=2) + "\n")
print(f"Updated {config_path}")
PY

echo
echo "==> Done."
echo "    1. Quit Claude Desktop completely (Cmd+Q), then reopen it."
echo "    2. Look for the hammer/tools icon — aws-api should be connected."
echo "    3. See README.md for Dispatch (mobile) pairing steps."
echo
echo "    Config: ${CLAUDE_CONFIG}"
