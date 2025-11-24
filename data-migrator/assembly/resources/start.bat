@echo off
setlocal enabledelayedexpansion

REM Get the directory of the script
set "BASEDIR=%~dp0"
set "PROPS_FILE=%BASEDIR%internal/launcher.properties"

REM Function to read property from file
for /f "tokens=1,* delims==" %%a in (%PROPS_FILE%) do (
    if "%%a"=="JAR_PATH" set "JAR_PATH_REL=%%b"
    if "%%a"=="CONFIGURATION" set "CONFIGURATION_REL=%%b"
    if "%%a"=="DEPLOYMENT_DIR" set "DEPLOYMENT_DIR_REL=%%b"
    if "%%a"=="CLASSPATH" set "CLASSPATH_REL=%%b"
    if "%%a"=="JAVA_OPTS_TEMPLATE" set "JAVA_OPTS_TEMPLATE=%%b"
)

REM Build full paths
set "JAR_PATH=%BASEDIR%%JAR_PATH_REL%"
set "CONFIGURATION=%BASEDIR%%CONFIGURATION_REL%"
set "DEPLOYMENT_DIR=%BASEDIR%%DEPLOYMENT_DIR_REL%"
set "CLASSPATH=%BASEDIR%%CLASSPATH_REL%"

REM Build Java options from template
set "JAVA_OPTS=!JAVA_OPTS_TEMPLATE!"
set "JAVA_OPTS=!JAVA_OPTS:{CLASSPATH}=%CLASSPATH%!"
set "JAVA_OPTS=!JAVA_OPTS:{DEPLOYMENT_DIR}=%DEPLOYMENT_DIR%!"
set "JAVA_OPTS=!JAVA_OPTS:{CONFIGURATION}=%CONFIGURATION%!"
REM Launch the JAR with system properties set as JVM arguments
java %JAVA_OPTS% -jar "%JAR_PATH%" %*