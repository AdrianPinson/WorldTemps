#!/bin/sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  java_bin="$JAVA_HOME/bin/java"
else
  java_bin=java
fi
exec "$java_bin" -cp "$app_dir/worldtemps-core.jar:$app_dir/lib/*" io.github.adrianpinson.worldtemps.ui.DesktopLauncher "$@"
