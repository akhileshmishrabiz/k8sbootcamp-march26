#!/usr/bin/env python3
"""Fix Terraform using OpenAI based on Checkov scan results."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

from openai import OpenAI

DEFAULT_MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")
DEFAULT_TEMPERATURE = float(os.environ.get("OPENAI_TEMPERATURE", "0.1"))
FILE_MARKER_RE = re.compile(r"^#\s*file:\s*(.+?)\s*$", re.IGNORECASE)

SYSTEM_PROMPT = """\
You are a Terraform and AWS security expert. Given Terraform files and Checkov failed_checks,
update the Terraform so those specific Checkov rules pass.

Rules:
- Fix only what is needed to address the listed Checkov failures.
- Keep existing resource names and structure where possible.
- Use current AWS provider patterns (separate resources for versioning, encryption, public access block, logging).
- Do not add unrelated resources or refactor beyond what Checkov requires.
- Output ONLY Terraform HCL. No markdown fences, no commentary.

Format each file exactly like this:
# file: main.tf
<contents>

# file: other.tf
<contents>
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fix Terraform using OpenAI and Checkov scan results."
    )
    parser.add_argument(
        "--terraform-dir",
        default=".",
        help="Directory containing Terraform files (default: current directory)",
    )
    parser.add_argument(
        "--scan-results",
        default=os.environ.get("SCAN_RESULTS_FILE", "checkov-results.json"),
        help="Path to Checkov JSON output",
    )
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"OpenAI model to use (default: {DEFAULT_MODEL})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print fixed files to stdout instead of writing them",
    )
    return parser.parse_args()


def load_failed_checks(path: Path) -> list[dict]:
    if not path.is_file():
        print(f"Error: scan results not found: {path}", file=sys.stderr)
        sys.exit(1)

    data = json.loads(path.read_text(encoding="utf-8"))
    failed = data.get("results", {}).get("failed_checks", [])
    if not failed:
        print("No failed Checkov checks; nothing to fix.")
        sys.exit(0)
    return failed


def summarize_failures(failed_checks: list[dict]) -> str:
    summary = []
    for check in failed_checks:
        summary.append(
            {
                "check_id": check.get("check_id"),
                "check_name": check.get("check_name"),
                "file_path": check.get("file_path"),
                "resource": check.get("resource"),
                "guideline": check.get("guideline"),
            }
        )
    return json.dumps(summary, indent=2)


def collect_terraform_files(terraform_dir: Path) -> dict[str, str]:
    files = {}
    for path in sorted(terraform_dir.glob("*.tf")):
        files[path.name] = path.read_text(encoding="utf-8")
    if not files:
        print(f"Error: no .tf files found in {terraform_dir}", file=sys.stderr)
        sys.exit(1)
    return files


def format_files_for_prompt(files: dict[str, str]) -> str:
    parts = []
    for name, content in files.items():
        parts.append(f"# file: {name}\n{content.rstrip()}\n")
    return "\n".join(parts)


def parse_fixed_files(content: str) -> dict[str, str]:
    files: dict[str, str] = {}
    current_name: str | None = None
    current_lines: list[str] = []

    for line in content.splitlines():
        match = FILE_MARKER_RE.match(line.strip())
        if match:
            if current_name is not None:
                files[current_name] = "\n".join(current_lines).rstrip() + "\n"
            current_name = Path(match.group(1)).name
            current_lines = []
            continue
        if current_name is not None:
            current_lines.append(line)

    if current_name is not None:
        files[current_name] = "\n".join(current_lines).rstrip() + "\n"

    if not files:
        print("Error: could not parse fixed Terraform from model response.", file=sys.stderr)
        sys.exit(1)
    return files


def strip_markdown_fences(content: str) -> str:
    text = content.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines)
    return text.strip() + "\n"


def fix_terraform(
    client: OpenAI,
    terraform_files: str,
    failures: str,
    model: str,
    temperature: float,
) -> dict[str, str]:
    user_prompt = f"""Fix the Terraform below to resolve these Checkov failures.

## Checkov failures
{failures}

## Current Terraform
{terraform_files}
"""
    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        temperature=temperature,
    )
    fixed = response.choices[0].message.content or ""
    if not fixed.strip():
        print("Error: OpenAI returned an empty response.", file=sys.stderr)
        sys.exit(1)
    return parse_fixed_files(strip_markdown_fences(fixed))


def main() -> None:
    args = parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Error: OPENAI_API_KEY environment variable is not set.", file=sys.stderr)
        sys.exit(1)

    terraform_dir = Path(args.terraform_dir).resolve()
    scan_results_path = Path(args.scan_results).resolve()

    failed_checks = load_failed_checks(scan_results_path)
    failures = summarize_failures(failed_checks)
    original_files = collect_terraform_files(terraform_dir)
    prompt_files = format_files_for_prompt(original_files)

    client = OpenAI(api_key=api_key)
    fixed_files = fix_terraform(
        client,
        prompt_files,
        failures,
        args.model,
        DEFAULT_TEMPERATURE,
    )

    if args.dry_run:
        print(format_files_for_prompt(fixed_files), end="")
        return

    changed = False
    for name, content in fixed_files.items():
        target = terraform_dir / name
        if not target.exists():
            print(f"Skipping unknown file from model: {name}")
            continue
        if content != original_files.get(name):
            target.write_text(content, encoding="utf-8")
            print(f"Updated {target}")
            changed = True

    if not changed:
        print("Terraform unchanged after fix attempt.")


if __name__ == "__main__":
    main()
