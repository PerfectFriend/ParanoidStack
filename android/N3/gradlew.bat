@echo off
set DIRNAME=%~dp0
if "%OS%"=="Windows_NT" setlocal
set CLASSPATH=%DIRNAME%wrapper/gradle-wrapper.jar
"%JAVA_HOME%/bin/java" -cp %CLASSPATH% org.gradle.wrapper.GradleWrapperMain %*
