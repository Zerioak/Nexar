#!/bin/sh
# Gradle wrapper script for NEXAR
# This is a standard Gradle wrapper - copy the gradlew from any Android project
# or run: gradle wrapper --gradle-version=8.4 in the project root

DIRNAME="$(dirname "$0")"
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper JAR not found."
    echo "Please add gradle/wrapper/gradle-wrapper.jar"
    echo "You can get it by running 'gradle wrapper --gradle-version=8.4' or copying from any Android project."
    exit 1
fi

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
