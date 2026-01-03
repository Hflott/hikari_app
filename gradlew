#!/usr/bin/env sh
set -eu
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: Missing $WRAPPER_JAR" >&2
  echo "Download it from: https://services.gradle.org/distributions/gradle-8.9-wrapper.jar" >&2
  exit 1
fi

JAVA_EXE="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

exec "$JAVA_EXE" -jar "$WRAPPER_JAR" "$@"
