#!/bin/sh
set -e

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
MAVEN_HOME="$WRAPPER_DIR/apache-maven-3.9.9"
MAVEN_ZIP="$WRAPPER_DIR/apache-maven-3.9.9-bin.zip"
DISTRIBUTION_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

if [ -z "$JAVA_HOME" ]; then
  echo "Error: JAVA_HOME not found in your environment." >&2
  exit 1
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Error: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
  exit 1
fi

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Apache Maven from $DISTRIBUTION_URL"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DISTRIBUTION_URL" -o "$MAVEN_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DISTRIBUTION_URL" -O "$MAVEN_ZIP"
  else
    echo "Error: curl or wget is required to download Maven." >&2
    exit 1
  fi
  unzip -q "$MAVEN_ZIP" -d "$WRAPPER_DIR"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
