#!/usr/bin/env bash
# Install git hooks for this project.
# Usage: ./scripts/install-hooks.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"

mkdir -p "$HOOKS_DIR"

# Install pre-commit hook
cat > "$HOOKS_DIR/pre-commit" << 'HOOK'
#!/usr/bin/env bash
# Pre-commit hook: runs code quality checks.
# Bypass with: git commit --no-verify (use sparingly, WIP only)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "Running pre-commit checks..."
"$PROJECT_ROOT/scripts/code-quality-check.sh"
HOOK

chmod +x "$HOOKS_DIR/pre-commit"

# Install commit-msg hook (enforce conventional message format)
cat > "$HOOKS_DIR/commit-msg" << 'HOOK'
#!/usr/bin/env bash
# Commit message hook: validates format.
# Expected: short summary line (max 72 chars), optional body after blank line.

set -euo pipefail

MSG_FILE="$1"
FIRST_LINE=$(head -n 1 "$MSG_FILE")

if [ ${#FIRST_LINE} -gt 72 ]; then
    echo "FAIL: Commit message first line is ${#FIRST_LINE} chars (max: 72)."
    echo "Shorten your summary line."
    exit 1
fi

if [ ${#FIRST_LINE} -lt 3 ]; then
    echo "FAIL: Commit message is too short. Write a meaningful summary."
    exit 1
fi
HOOK

chmod +x "$HOOKS_DIR/commit-msg"

echo "Git hooks installed successfully."
echo "  - pre-commit: runs code-quality-check.sh"
echo "  - commit-msg: validates message format (max 72 char summary)"
