@echo off
setlocal
set "DIR=%~dp0"
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" %*
  ) else (
    java -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" %*
  )
  exit /b %ERRORLEVEL%
)

echo.
echo ERROR: Missing Gradle wrapper JAR:
 echo   %DIR%gradle\wrapper\gradle-wrapper.jar
 echo.
echo Fix (PowerShell):
 echo   mkdir gradle\wrapper -Force
 echo   iwr https://services.gradle.org/distributions/gradle-8.9-wrapper.jar -OutFile gradle\wrapper\gradle-wrapper.jar
 echo.
echo Then re-run:
 echo   .\gradlew.bat --version
exit /b 1
