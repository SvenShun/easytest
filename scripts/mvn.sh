#!/usr/bin/env bash
# Maven wrapper for this workspace.
# The DSH sandbox only allows writes inside the workspace, so the local
# repository lives in .m2/repository instead of ~/.m2/repository.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec mvn -Dmaven.repo.local="$ROOT/.m2/repository" "$@"
