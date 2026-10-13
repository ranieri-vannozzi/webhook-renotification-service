#!/usr/bin/env bash
#
# Runs the full build from scratch with the JDK required by the project:
# compilation with NullAway, unit and architecture tests, packaging.
#
# The same script runs in CI, so a failing pipeline can be reproduced locally
# with exactly the same command. When the CI environment variable is set,
# Maven runs in batch mode without download progress output.
#
# Usage:
#   ./verify.sh [extra Maven arguments]
#
# The JDK identifier can be overridden with the JAVA_VERSION environment variable.

set -o pipefail

readonly LOG_PREFIX="verify"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1
readonly PROJECT_DIR

# shellcheck source=scripts/jdk.sh
source "$PROJECT_DIR/scripts/jdk.sh"

ensure_jdk
enter_project "$PROJECT_DIR"

maven_args=()
if [[ -n "${CI:-}" ]]; then
  maven_args+=(--batch-mode --no-transfer-progress)
fi

exec ./mvnw "${maven_args[@]}" clean verify "$@"
