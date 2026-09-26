#!/bin/sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"

chmod +x .githooks/pre-commit
git config --local core.hooksPath .githooks

echo "AuditAgent Git hooks enabled from .githooks."
