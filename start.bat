@echo off
chcp 65001 >nul
cd /d "%~dp0"
REM 优先查找项目内的 jdk17，其次查找项目上一级的 jdk17
if exist "%~dp0jdk17\jdk-17.0.20.1+1\bin\java.exe" (
    set "JAVA_HOME=%~dp0jdk17\jdk-17.0.20.1+1"
) else if exist "%~dp0..\jdk17\jdk-17.0.20.1+1\bin\java.exe" (
    set "JAVA_HOME=%~dp0..\jdk17\jdk-17.0.20.1+1"
) else (
    echo 未找到 JDK 17，请确认 jdk17 文件夹位于 TanghuluLauncher 或 TanghuluLauncher\TanghuluLauncher 目录下。
    pause
    exit /b 1
)
echo 正在启动 Tanghulu Launcher ...
call gradlew.bat run
if errorlevel 1 (
    echo.
    echo 启动失败，请把上面的报错信息发给我。
    pause
)
