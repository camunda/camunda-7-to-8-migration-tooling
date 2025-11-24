#!/bin/bash
BASEDIR=$(dirname "$0")

# Load configuration from central properties file
PROPS_FILE="$BASEDIR/internal/launcher.properties"

# Function to read property from file
get_property() {
    grep "^$1=" "$PROPS_FILE" | cut -d'=' -f2-
}

# Read configuration values
JAR_PATH="$BASEDIR/$(get_property "JAR_PATH")"
CONFIGURATION="$BASEDIR/$(get_property "CONFIGURATION")"
DEPLOYMENT_DIR="$BASEDIR/$(get_property "DEPLOYMENT_DIR")"
CLASSPATH="$BASEDIR/$(get_property "CLASSPATH")"

# Build Java options from template
JAVA_OPTS_TEMPLATE=$(get_property "JAVA_OPTS_TEMPLATE")
JAVA_OPTS="${JAVA_OPTS_TEMPLATE//\{CLASSPATH\}/$CLASSPATH}"
JAVA_OPTS="${JAVA_OPTS//\{DEPLOYMENT_DIR\}/$DEPLOYMENT_DIR}"
JAVA_OPTS="${JAVA_OPTS//\{CONFIGURATION\}/$CONFIGURATION}"

# Launch the JAR with system properties set as JVM arguments
java $JAVA_OPTS -jar "$JAR_PATH" "$@"
