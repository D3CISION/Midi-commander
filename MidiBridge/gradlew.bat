@echo off
setlocal
rem Download and verify the official wrapper, then forward arguments unchanged to Java.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\gradle-bootstrap.ps1"
if errorlevel 1 exit /b 1
pushd "%~dp0"
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  java.exe -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
)
set CODE=%ERRORLEVEL%
popd
exit /b %CODE%
