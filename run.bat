@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ==================================================================
rem  大学生个人学业档案与证书日程管理系统 · MVP 原型 启动脚本
rem  软件体系结构课程 第 5 周作业
rem
rem  脚本会：1) 定位 JDK  2) 用 Maven 编译并解析依赖  3) 直接启动 java
rem  首次启动会自动生成演示数据，无需手工准备。
rem ==================================================================

cd /d "%~dp0"

rem ---------- 1. 定位 JDK ----------
set "JDK="
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"
)

if not defined JDK (
  for %%D in (
    "D:\IntelliJ IDEA 2025.2.1\jbr"
    "C:\Program Files\Eclipse Adoptium\jdk-21*"
    "C:\Program Files\Java\jdk-21*"
    "C:\Program Files\Microsoft\jdk-21*"
  ) do (
    if not defined JDK (
      if exist "%%~fD\bin\javac.exe" set "JDK=%%~fD"
    )
  )
)

if not defined JDK (
  for /f "delims=" %%J in ('where javac 2^>nul') do (
    if not defined JDK set "JDK=%%~dpJ.."
  )
)

if not defined JDK (
  echo [错误] 未找到 JDK（需要 JDK 17 或更高版本）。
  echo        请安装 JDK 后设置 JAVA_HOME 环境变量，例如：
  echo            set JAVA_HOME=C:\Program Files\Java\jdk-21
  echo.
  pause
  exit /b 1
)
set "JAVA_HOME=%JDK%"
echo [环境] JDK  = %JAVA_HOME%

rem ---------- 2. 定位 Maven ----------
set "MVN="
where mvn >nul 2>nul && set "MVN=mvn"
if not defined MVN (
  if exist "D:\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd" (
    set "MVN=D:\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd"
  )
)
if not defined MVN (
  echo [错误] 未找到 Maven。请安装 Maven 并加入 PATH。
  pause
  exit /b 1
)
echo [环境] Maven = %MVN%

rem ---------- 3. 编译并解析依赖 ----------
if not exist "target\classes" (
  echo [1/2] 首次运行，正在编译并下载依赖（需要联网，仅此一次）...
) else (
  echo [1/2] 正在编译并同步依赖...
)
call "%MVN%" -q -B compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
if errorlevel 1 (
  echo.
  echo [错误] 编译失败，请检查上面的错误信息。
  pause
  exit /b 1
)

rem ---------- 4. 启动 ----------
echo [2/2] 正在启动原型...
echo.
echo   数据目录：%USERPROFILE%\.archive-app
echo   首次启动会自动生成演示数据；关闭窗口即可退出。
echo.

rem cp.txt 是 Maven 生成的依赖 classpath（单行），这里读进来拼成启动参数
set "CP="
set /p CP=<target\cp.txt
if not defined CP (
  echo [错误] 无法读取 target\cp.txt，请重新运行本脚本。
  pause
  exit /b 1
)

java -Dfile.encoding=UTF-8 -cp "target\classes;!CP!" com.campus.archive.Launcher

endlocal
