# shellcheck shell=bash
# Shared helpers for the project scripts: logging and JDK activation.
#
# Source this file after defining LOG_PREFIX. The JDK identifier can be
# overridden with the JAVA_VERSION environment variable.

readonly JAVA_VERSION="${JAVA_VERSION:-25.0.1.0+8-amzn}"
readonly SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
readonly SDKMAN_INIT="$SDKMAN_DIR/bin/sdkman-init.sh"

log() {
  printf '[%s] %s\n' "$LOG_PREFIX" "$*"
}

fail() {
  printf '[%s] ERROR: %s\n' "$LOG_PREFIX" "$*" >&2
  exit 1
}

# The java binary the Maven wrapper will pick: JAVA_HOME first, then PATH.
current_java() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    printf '%s/bin/java' "$JAVA_HOME"
  else
    printf 'java'
  fi
}

current_java_major() {
  "$(current_java)" -XshowSettings:properties -version 2>&1 \
    | awk -F' = ' '/java.specification.version/ { print $2 }'
}

# Keeps the active JDK when its major version already matches (e.g. CI with
# actions/setup-java), otherwise installs and activates the pinned JDK through
# SDKMAN for the current shell only, leaving the global default untouched.
ensure_jdk() {
  local required_major="${JAVA_VERSION%%.*}"

  if command -v "$(current_java)" >/dev/null 2>&1 \
    && [[ "$(current_java_major)" == "$required_major" ]]; then
    log "Using active JDK $required_major: $("$(current_java)" -version 2>&1 | head -n 1)"
    return
  fi

  # SDKMAN is a set of shell functions, not a binary on PATH, so it must be
  # sourced into this shell before "sdk" can be called.
  if [[ ! -s "$SDKMAN_INIT" ]]; then
    fail "JDK $required_major is not active and SDKMAN was not found in $SDKMAN_DIR. Install it with: curl -s \"https://get.sdkman.io\" | bash"
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
}

# Moves into the project directory and makes sure the Maven wrapper can run.
enter_project() {
  cd "$1" || fail "Unable to enter project directory $1"

  if [[ ! -x ./mvnw ]]; then
    chmod +x ./mvnw || fail "Maven wrapper is not executable"
  fi
}
