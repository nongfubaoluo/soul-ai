#!/bin/sh
# Gradle wrapper script
set -e
SCRIPT_DIR=$(cd "$(dirname "$0")" ; pwd -P)
SCRIPT_NAME=$(basename "$0")
unset JAVA_HOME
if [ -z "$JAVA_HOME" ] ; then
  JAVA_EXE=`which java`
else
  JAVA_EXE="$JAVA_HOME/bin/java"
fi
exec "$JAVA_EXE" -Dorg.gradle.appname=$SCRIPT_NAME -classpath "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
