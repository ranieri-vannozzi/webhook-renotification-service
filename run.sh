#!/usr/bin/env bash
#
# Starts the service locally with the JDK required by the project.
#
# Ensures SDKMAN is available, installs the required JDK if missing,
# activates it for this shell only (the global default is left untouched)
# and runs the application through the Maven wrapper.
#
# Usage:
#   ./run.sh [extra spring-boot:run arguments]
#
# The JDK identifier can be overridden with the JAVA_VERSION environment variable.

set -o pipefail

readonly JAVA_VERSION="${JAVA_VERSION:-25.0.1.0+8-amzn}"
readonly SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
readonly SDKMAN_INIT="$SDKMAN_DIR/bin/sdkman-init.sh"
readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() {
  printf '[run] %s\n' "$*"
}

fail() {
  printf '[run] ERROR: %s\n' "$*" >&2
  exit 1
}

# SDKMAN is a set of shell functions, not a binary on PATH, so it must be
# sourced into this shell before "sdk" can be called.
if [[ ! -s "$SDKMAN_INIT" ]]; then
  fail "SDKMAN not found in $SDKMAN_DIR. Install it with: curl -s \"https://get.sdkman.io\" | bash"
fi

# shellcheck source=/dev/null
source "$SDKMAN_INIT"

if ! command -v sdk >/dev/null 2>&1; then
  fail "SDKMAN init script was sourced but the 'sdk' command is not available."
fi

log "SDKMAN found in $SDKMAN_DIR"

if [[ -d "$SDKMAN_DIR/candidates/java/$JAVA_VERSION" ]]; then
  log "JDK $JAVA_VERSION already installed"
else
  log "Installing JDK $JAVA_VERSION"
  # Answer "n" to the "set as default" prompt so the global default stays unchanged.
  if ! echo "n" | sdk install java "$JAVA_VERSION"; then
    fail "Unable to install JDK $JAVA_VERSION. Check the identifier with: sdk list java"
  fi
fi

# "sdk use" only affects the current shell, which is what we want here.
if ! sdk use java "$JAVA_VERSION" >/dev/null; then
  fail "Unable to activate JDK $JAVA_VERSION"
fi

log "Using $(java -version 2>&1 | head -n 1)"

cd "$PROJECT_DIR" || fail "Unable to enter project directory $PROJECT_DIR"

if [[ ! -x ./mvnw ]]; then
  chmod +x ./mvnw || fail "Maven wrapper is not executable"
fi

exec ./mvnw spring-boot:run "$@"
