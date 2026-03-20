#!/usr/bin/env bash
# Check that no tracked file exceeds the max line limit.
# Usage: ./scripts/check-file-length.sh [MAX_LINES]
# Default: 500 lines

set -euo pipefail

MAX_LINES="${1:-500}"
FAILED=0

# Get files: if in a git repo with commits, use tracked files; otherwise use staged files
if git rev-parse HEAD >/dev/null 2>&1; then
    FILES=$(git ls-files)
else
    FILES=$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null || find . -type f -not -path './.git/*')
fi

while IFS= read -r file; do
    # Skip empty lines
    [ -z "$file" ] && continue

    # Skip non-existent files (deleted)
    [ -f "$file" ] || continue

    # Skip binary files
    file --mime "$file" 2>/dev/null | grep -q "binary" && continue

    # Skip vendored/generated files
    case "$file" in
        */build/*|*/node_modules/*|*.lock|*.min.*|*.generated.*|*/vendor/*) continue ;;
    esac

    LINE_COUNT=$(wc -l < "$file")
    if [ "$LINE_COUNT" -gt "$MAX_LINES" ]; then
        echo "FAIL: $file has $LINE_COUNT lines (max: $MAX_LINES)"
        FAILED=1
    fi
done <<< "$FILES"

if [ "$FAILED" -eq 1 ]; then
    echo ""
    echo "Files above exceed the $MAX_LINES-line limit. Split them before committing."
    exit 1
else
    echo "OK: All files are within the $MAX_LINES-line limit."
fi
