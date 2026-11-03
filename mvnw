#!/bin/sh
set -e
MAVEN_PROJECTBASEDIR="${0%/*}"
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
if [ ! -r "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper jar..."
  mkdir -p "$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
  curl -fsSL https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar -o "$WRAPPER_JAR"
fi

if [ -r "$WRAPPER_JAR" ]; then
  exec java -jar "$WRAPPER_JAR" "$@"
else
  echo "Maven wrapper jar not found. Please install maven or download maven-wrapper.jar." >&2
  exit 1
fi
