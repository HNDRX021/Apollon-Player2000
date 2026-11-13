#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA_EXE="$JAVA_HOME/bin/java"; elif command -v java >/dev/null 2>&1; then JAVA_EXE=java; else echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2; exit 1; fi
exec "$JAVA_EXE" ${DEFAULT_JVM_OPTS:-} ${JAVA_OPTS:-} ${GRADLE_OPTS:-} "-Dorg.gradle.appname=gradlew" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
