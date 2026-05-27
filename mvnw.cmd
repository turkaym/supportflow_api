@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
set "WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"
set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-3.9.9"
set "MAVEN_ZIP=%WRAPPER_DIR%\apache-maven-3.9.9-bin.zip"
set "DISTRIBUTION_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

if "%JAVA_HOME%"=="" (
  echo Error: JAVA_HOME not found in your environment. 1>&2
  exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Error: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
  exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  echo Downloading Apache Maven from %DISTRIBUTION_URL%
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%MAVEN_ZIP%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%WRAPPER_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
