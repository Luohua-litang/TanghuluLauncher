@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  TanghuluLauncher startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and TANGHULU_LAUNCHER_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\TanghuluLauncher-1.0.0.jar;%APP_HOME%\lib\desktop-jvm-1.7.3.jar;%APP_HOME%\lib\skiko-awt-runtime-windows-x64-0.8.18.jar;%APP_HOME%\lib\material3-desktop-1.7.3.jar;%APP_HOME%\lib\material-desktop-1.7.3.jar;%APP_HOME%\lib\material-ripple-desktop-1.7.3.jar;%APP_HOME%\lib\foundation-desktop-1.7.3.jar;%APP_HOME%\lib\material-icons-extended-desktop-1.7.3.jar;%APP_HOME%\lib\material-icons-core-desktop-1.7.3.jar;%APP_HOME%\lib\animation-desktop-1.7.3.jar;%APP_HOME%\lib\animation-core-desktop-1.7.3.jar;%APP_HOME%\lib\foundation-layout-desktop-1.7.3.jar;%APP_HOME%\lib\ui-desktop-1.7.3.jar;%APP_HOME%\lib\ui-text-desktop-1.7.3.jar;%APP_HOME%\lib\ui-graphics-desktop-1.7.3.jar;%APP_HOME%\lib\skiko-awt-0.8.18.jar;%APP_HOME%\lib\ui-tooling-preview-desktop-1.7.3.jar;%APP_HOME%\lib\ui-unit-desktop-1.7.3.jar;%APP_HOME%\lib\ui-geometry-desktop-1.7.3.jar;%APP_HOME%\lib\runtime-saveable-desktop-1.7.3.jar;%APP_HOME%\lib\lifecycle-runtime-compose-desktop-2.8.4.jar;%APP_HOME%\lib\runtime-desktop-1.7.3.jar;%APP_HOME%\lib\lifecycle-viewmodel-desktop-2.8.5.jar;%APP_HOME%\lib\lifecycle-runtime-desktop-2.8.5.jar;%APP_HOME%\lib\lifecycle-common-jvm-2.8.5.jar;%APP_HOME%\lib\kotlinx-coroutines-core-jvm-1.8.1.jar;%APP_HOME%\lib\kotlinx-coroutines-swing-1.8.1.jar;%APP_HOME%\lib\ui-util-desktop-1.7.3.jar;%APP_HOME%\lib\kotlin-stdlib-jdk8-1.9.24.jar;%APP_HOME%\lib\atomicfu-jvm-0.23.2.jar;%APP_HOME%\lib\kotlinx-datetime-jvm-0.6.0.jar;%APP_HOME%\lib\kotlin-stdlib-jdk7-1.9.24.jar;%APP_HOME%\lib\collection-jvm-1.4.0.jar;%APP_HOME%\lib\core-common-2.2.0.jar;%APP_HOME%\lib\annotation-jvm-1.8.0.jar;%APP_HOME%\lib\kotlin-stdlib-2.0.21.jar;%APP_HOME%\lib\annotations-23.0.0.jar


@rem Execute TanghuluLauncher
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %TANGHULU_LAUNCHER_OPTS%  -classpath "%CLASSPATH%" com.tanghulu.launcher.MainKt %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable TANGHULU_LAUNCHER_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%TANGHULU_LAUNCHER_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
