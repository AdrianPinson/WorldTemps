@echo off
setlocal
set "APP_DIR=%~dp0"
if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java"
)
"%JAVA_BIN%" -cp "%APP_DIR%worldtemps-core.jar;%APP_DIR%lib\*" io.github.adrianpinson.worldtemps.ui.DesktopLauncher %*
exit /b %errorlevel%
