#!/bin/sh
set -e
MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
if [ ! -r "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper jar..."
  mkdir -p "$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar -O "$WRAPPER_JAR"
  else
    echo "Maven Wrapper jar is missing and neither curl nor wget is available." >&2
    exit 1
  fi
fi

if [ -r "$WRAPPER_JAR" ]; then
  exec java "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
    -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
else
  echo "Maven wrapper jar not found. Please install maven or download maven-wrapper.jar." >&2
  exit 1
fi
