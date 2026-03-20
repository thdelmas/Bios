#!/usr/bin/env bash
# Scan staged files for potential hardcoded secrets.
# Usage: ./scripts/check-secrets.sh

set -euo pipefail

FAILED=0

# Patterns that suggest hardcoded secrets
PATTERNS=(
    'API_KEY\s*=\s*"[^"]{8,}'
    'api_key\s*=\s*"[^"]{8,}'
    'SECRET\s*=\s*"[^"]{8,}'
    'secret\s*=\s*"[^"]{8,}'
    'PASSWORD\s*=\s*"[^"]{3,}'
    'password\s*=\s*"[^"]{3,}'
    'TOKEN\s*=\s*"[^"]{8,}'
    'token\s*=\s*"[^"]{8,}'
    'PRIVATE_KEY\s*=\s*"'
    'BEGIN RSA PRIVATE KEY'
    'BEGIN OPENSSH PRIVATE KEY'
    'BEGIN EC PRIVATE KEY'
)

# Get staged files (or all files if no commits yet)
if git rev-parse HEAD >/dev/null 2>&1; then
    FILES=$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null || echo "")
else
    FILES=$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null || find . -type f -not -path './.git/*')
fi

[ -z "$FILES" ] && { echo "OK: No staged files to check."; exit 0; }

for pattern in "${PATTERNS[@]}"; do
    MATCHES=$(echo "$FILES" | xargs grep -lnE "$pattern" 2>/dev/null || true)
    if [ -n "$MATCHES" ]; then
        echo "WARN: Potential secret found matching pattern '$pattern' in:"
        echo "$MATCHES" | sed 's/^/  /'
        FAILED=1
    fi
done

if [ "$FAILED" -eq 1 ]; then
    echo ""
    echo "Review the files above. Remove secrets and use environment variables or a secrets manager instead."
    exit 1
else
    echo "OK: No hardcoded secrets detected in staged files."
fi
