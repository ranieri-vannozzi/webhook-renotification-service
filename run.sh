#!/usr/bin/env bash
#
# Starts the service locally with the JDK required by the project.
#
# Uses the active JDK when it already has the required major version,
# otherwise installs it through SDKMAN if missing and activates it for this
# shell only (the global default is left untouched), then runs the
# application through the Maven wrapper.
#
# Usage:
#   ./run.sh [extra spring-boot:run arguments]
#
# The JDK identifier can be overridden with the JAVA_VERSION environment variable.

set -o pipefail

readonly LOG_PREFIX="run"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1
readonly PROJECT_DIR

# shellcheck source=scripts/jdk.sh
source "$PROJECT_DIR/scripts/jdk.sh"

ensure_jdk
enter_project "$PROJECT_DIR"

exec ./mvnw spring-boot:run "$@"
