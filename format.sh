#!/usr/bin/env bash
#
# Formats the Java code with Spotless, using the JDK required by the project.
#
# Applies the Eclipse formatter settings in .spotless/eclipse-formatter.prefs,
# removes unused imports and trailing whitespace. ./verify.sh (and so CI)
# fails on code that has not been formatted this way.
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

ensure_jdk
enter_project "$PROJECT_DIR"

exec ./mvnw spotless:apply "$@"
