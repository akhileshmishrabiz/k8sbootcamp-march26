# AWS MCP for Claude Desktop + Dispatch (Mobile)

Talk to AWS from Claude using the **official** [AWS API MCP Server](https://github.com/awslabs/mcp/tree/main/src/aws-api-mcp-server) (`awslabs.aws-api-mcp-server`).

This setup is designed for:

- **Claude Desktop** on your Mac (MCP runs locally with your AWS credentials)
- **Dispatch** from the Claude mobile app (tasks run on your laptop while you're away)

```
┌─────────────────┐     Dispatch      ┌──────────────────────────────┐
│  Claude Mobile  │ ───────────────►  │  Claude Desktop (your Mac)   │
│  (iOS/Android)  │   task + reply    │  ┌────────────────────────┐  │
└─────────────────┘                   │  │ AWS API MCP Server     │  │
                                      │  │ (uvx, local stdio)     │  │
                                      │  └───────────┬────────────┘  │
                                      └──────────────┼───────────────┘
                                                     │ AWS CLI / API
                                                     ▼
                                              ┌─────────────┐
                                              │  AWS Account │
                                              └─────────────┘
```

## What you get

The official server exposes tools such as:

| Tool | Purpose |
|------|---------|
| `call_aws` | Run validated AWS CLI commands |
| `suggest_aws_commands` | Suggest CLI commands from natural language |
| `get_execution_plan` | Step-by-step AWS workflows (experimental) |

Example prompts:

- "List all EC2 instances in us-east-1"
- "Show S3 buckets and their public access settings"
- "Describe EKS clusters in my account"

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| macOS | Paths in this guide target Mac; Linux works with path adjustments |
| [Claude Desktop](https://claude.com/download) | Latest version |
| [Claude mobile app](https://claude.com/download) | For Dispatch |
| **Claude Pro or Max** | Dispatch is a paid-plan research preview |
| AWS account + credentials | `aws configure` or SSO |
| Python 3.10+ | Usually preinstalled or via Homebrew |
| AWS CLI v2 | `brew install awscli` |

## Quick setup (5 minutes)

### 1. Configure AWS credentials

Use a profile with the least privilege you need (ReadOnly for exploration, scoped IAM for day-to-day):

```bash
aws configure --profile default
# or SSO:
aws sso login --profile your-profile
aws sts get-caller-identity --profile your-profile
```

### 2. Optional: tune settings

```bash
cd AIops-projects/aws-mcp
cp .env.example .env
# Edit AWS_PROFILE, AWS_REGION, READ_OPERATIONS_ONLY
```

### 3. Run the installer

```bash
chmod +x setup.sh verify.sh
./setup.sh
```

This will:

1. Install `uv` / `uvx` if missing
2. Verify Python, AWS CLI, and credentials
3. Merge the `aws-api` MCP server into Claude Desktop config

### 4. Restart Claude Desktop

Fully quit Claude Desktop (**Cmd+Q**), then reopen. You should see a **tools/hammer** icon when the AWS MCP server is available.

### 5. Verify

```bash
./verify.sh
```

Or ask Claude in Desktop chat:

> Use the AWS MCP tools to run `aws sts get-caller-identity` and tell me which account I'm in.

---

## Dispatch from mobile

Dispatch lets you send a task from your phone; Claude runs it **on your Mac** using local MCP servers (including AWS).

### One-time pairing

1. Open **Claude Desktop** on your Mac.
2. Switch to the **Cowork** tab.
3. Follow the in-app steps to **pair your mobile device** (QR code).
4. Install/update the **Claude mobile app** and sign in with the same account.

### Before you leave your desk

1. Keep **Claude Desktop open** (not just minimized — the app must be running).
2. In Dispatch settings, enable **Keep awake** so your Mac doesn't sleep mid-task.
3. Ensure your Mac has network access and valid AWS credentials (SSO sessions expire — run `aws sso login` if needed).

### Send an AWS task from mobile

1. Open Claude on your phone.
2. Tap **Dispatch**.
3. Send a clear, self-contained prompt, for example:

   > On my laptop, use AWS MCP to list all running EC2 instances in us-east-1. Summarize instance IDs, types, and names. Reply when done.

4. You'll get a push notification when the task finishes or needs input.

### How Dispatch uses MCP

- MCP servers in `~/Library/Application Support/Claude/claude_desktop_config.json` load into **Desktop chat** and **Code tab** sessions.
- Dispatch runs work on your machine, so the **AWS MCP server uses your local `~/.aws` credentials** — same as Desktop chat.
- Dispatch is for **delegating tasks**, not live steering. For real-time control of an active CLI session, see [Remote Control](https://code.claude.com/docs/en/platforms) instead.

### Example mobile prompts

| Goal | Dispatch prompt |
|------|-----------------|
| Inventory | "List all Lambda functions in us-west-2 with runtime and last modified date." |
| Cost check | "Use AWS MCP to list S3 buckets over 1 GB (approximate size if available)." |
| Health | "Describe CloudWatch alarms in ALARM state in us-east-1." |
| Read-only safety | "Use read-only AWS commands only. List IAM users and their last activity." |

---

## Manual configuration

If you prefer not to run `setup.sh`, copy the example into Claude Desktop config:

**File:** `~/Library/Application Support/Claude/claude_desktop_config.json`

See [`claude_desktop_config.example.json`](claude_desktop_config.example.json).

Merge with existing `mcpServers` — do not replace other servers.

### Alternative: Claude Connector Directory

In Claude Desktop: **Settings → Connectors** → search **AWS API** → connect `awslabs.aws-api-mcp-server`.

Manual JSON config gives more control over env vars (`READ_OPERATIONS_ONLY`, profile name, etc.).

### Alternative: Docker

If you don't want `uvx` on the host:

```json
{
  "mcpServers": {
    "aws-api": {
      "command": "docker",
      "args": [
        "run", "--rm", "--interactive",
        "--env", "AWS_REGION=us-east-1",
        "--env", "AWS_API_MCP_PROFILE_NAME=default",
        "--volume", "/Users/YOUR_USER/.aws:/app/.aws",
        "public.ecr.aws/awslabs-mcp/awslabs/aws-api-mcp-server:latest"
      ],
      "env": {}
    }
  }
}
```

Replace `YOUR_USER` with your macOS username.

---

## Security recommendations

The MCP server runs **locally with your user's permissions**. Treat it like giving Claude access to your AWS CLI.

1. **Use scoped IAM** — prefer `ReadOnlyAccess` or a custom policy over `AdministratorAccess`.
2. **Enable read-only mode** for exploration:

   ```bash
   READ_OPERATIONS_ONLY=true ./setup.sh
   ```

   Or set `"READ_OPERATIONS_ONLY": "true"` in the Claude config env block.

3. **Optional consent for writes** — set `"REQUIRE_MUTATION_CONSENT": "true"` (requires client elicitation support).
4. **Custom deny list** — create `~/.aws/aws-api-mcp/mcp-security-policy.json` ([docs](https://awslabs.github.io/mcp/servers/aws-api-mcp-server#custom-security-policy-configuration)).
5. **Never commit credentials** — keep secrets in `~/.aws`, not in this repo.

Logs: `~/.aws/aws-api-mcp/aws-api-mcp-server.log`

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| No tools/hammer icon | Quit Claude fully (Cmd+Q), reopen; check JSON syntax in config |
| MCP server failed to start | Run `./verify.sh`; ensure `uvx` is on PATH (`~/.local/bin`) |
| AWS auth errors | `aws sso login --profile YOUR_PROFILE`; confirm profile name in config env |
| Dispatch task never starts | Desktop app closed or Mac asleep — enable Keep awake |
| SSO expired mid-task | Re-login on Mac before sending long Dispatch jobs |
| Wrong region | Set `AWS_REGION` in config env or `.env` and re-run `./setup.sh` |

### Test MCP outside Claude

```bash
# Should print help without error
uvx awslabs.aws-api-mcp-server@latest --help

# Confirm AWS access
aws sts get-caller-identity --profile default
```

### View Claude MCP logs

Claude Desktop → **Help → View Logs** (or check Console.app for `Claude`).

---

## Files in this directory

| File | Purpose |
|------|---------|
| `setup.sh` | Install uv, verify AWS, write Claude Desktop config |
| `verify.sh` | Pre-flight checks |
| `claude_desktop_config.example.json` | Reference MCP config |
| `.env.example` | Optional profile/region/safety flags |

## References

- [AWS API MCP Server (official docs)](https://awslabs.github.io/mcp/servers/aws-api-mcp-server)
- [awslabs/mcp GitHub](https://github.com/awslabs/mcp/tree/main/src/aws-api-mcp-server)
- [Dispatch tutorial](https://claude.com/resources/tutorials/dispatch-in-claude-cowork)
- [Claude Desktop + MCP](https://code.claude.com/docs/en/desktop)
