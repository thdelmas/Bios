#!/usr/bin/env bash
# Unified code quality check script.
# Run this before committing and in CI. Both should use this same script.
# Usage: ./scripts/code-quality-check.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FAILED=0

step() {
    echo ""
    echo "=== $1 ==="
}

# 1. File length check
step "Checking file lengths (max 500 lines)"
if ! "$SCRIPT_DIR/check-file-length.sh" 500; then
    FAILED=1
fi

# 2. Secret scanning
step "Scanning for hardcoded secrets"
if ! "$SCRIPT_DIR/check-secrets.sh"; then
    FAILED=1
fi

# 3. Android lint + compile (if Android project exists and gradlew is available)
if [ -f "$PROJECT_ROOT/android/gradlew" ]; then
    step "Running Android lint"
    if ! (cd "$PROJECT_ROOT/android" && ./gradlew lint 2>&1); then
        echo "WARN: Android lint failed (non-blocking until CI is set up)"
    fi

    # Only run tests if Kotlin files are staged
    KOTLIN_STAGED=$(git diff --cached --name-only --diff-filter=ACM -- '*.kt' '*.kts' 2>/dev/null || echo "")
    if [ -n "$KOTLIN_STAGED" ]; then
        step "Running Android unit tests"
        if ! (cd "$PROJECT_ROOT/android" && ./gradlew testDebugUnitTest 2>&1); then
            FAILED=1
        fi
    else
        step "Skipping Android tests (no Kotlin files staged)"
    fi
else
    step "Skipping Android checks (no gradlew found — run 'gradle wrapper' to set up)"
fi

# Summary
echo ""
echo "==============================="
if [ "$FAILED" -eq 1 ]; then
    echo "QUALITY CHECK FAILED"
    echo "Fix the issues above before committing."
    exit 1
else
    echo "ALL CHECKS PASSED"
fi
