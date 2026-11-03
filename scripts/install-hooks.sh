#!/usr/bin/env bash
# Point git at the versioned hooks in .githooks/ (pre-commit + commit-msg).
# Usage: ./scripts/install-hooks.sh
# Hooks live in the repo, so edits to them are reviewed and shared like code.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

chmod +x "$PROJECT_ROOT"/.githooks/*
git -C "$PROJECT_ROOT" config core.hooksPath .githooks

echo "Git hooks enabled from .githooks/ (core.hooksPath)."
echo "  - pre-commit: runs code-quality-check.sh"
echo "  - commit-msg: conventional commit format (max 72 char summary, ! = breaking)"
