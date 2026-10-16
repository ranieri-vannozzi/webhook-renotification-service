#!/usr/bin/env bash
#
# Formats the Java code with Spotless, using the JDK required by the project.
#
# Applies the Eclipse formatter settings in .spotless/eclipse-formatter.prefs,
# removes unused imports and trailing whitespace. ./verify.sh (and so CI)
# fails on code that has not been formatted this way.
#
# When Lefthook is installed, also activates the git hooks of lefthook.yml in
# this clone, so every commit gets formatted automatically. Lefthook itself is
# optional and never installed by this script.
#
# Usage:
#   ./format.sh [extra Maven arguments]
#
# The JDK identifier can be overridden with the JAVA_VERSION environment variable.

set -o pipefail

readonly LOG_PREFIX="format"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1
readonly PROJECT_DIR

# shellcheck source=scripts/jdk.sh
source "$PROJECT_DIR/scripts/jdk.sh"

# Activates the git hooks of lefthook.yml when Lefthook is available and they are missing or stale.
ensure_git_hooks() {
  if ! command -v lefthook >/dev/null 2>&1; then
    log "Optional: the project already configures Lefthook in lefthook.yml, just install it (https://lefthook.dev) to format the code on every commit"
    return
  fi
  # check-install only compares a checksum, so a deleted hook file must be checked separately
  if ! lefthook check-install >/dev/null 2>&1 || [[ ! -f "$(git rev-parse --git-path hooks/pre-commit)" ]]; then
    log "Activating the git hooks of lefthook.yml"
    lefthook install || log "Could not activate the git hooks, run 'lefthook install' manually"
  fi
}

ensure_jdk
enter_project "$PROJECT_DIR"
ensure_git_hooks

exec ./mvnw spotless:apply "$@"
