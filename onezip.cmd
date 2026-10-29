@echo off
setlocal
set "ONEZIP_JAR=%~dp0target\onezip-1.01-all.jar"
if exist "%ONEZIP_JAR%" goto run
pushd "%~dp0"
if errorlevel 1 exit /b 1
call mvnw.cmd -q -DskipTests package 1>&2
set "ONEZIP_BUILD_EXIT=%errorlevel%"
popd
if not "%ONEZIP_BUILD_EXIT%"=="0" exit /b %ONEZIP_BUILD_EXIT%
:run
set "ONEZIP_JAVA=java"
if defined JAVA_HOME set "ONEZIP_JAVA=%JAVA_HOME%\bin\java.exe"
"%ONEZIP_JAVA%" -Xms32m -jar "%ONEZIP_JAR%" %*
exit /b %errorlevel%
