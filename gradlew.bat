@rem
@rem Gradle startup script for Windows
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem  Gradle startup script for Windows
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Every run takes one of the machine's build slots before Gradle starts, waiting in the queue while all are held,
@rem and frees it when Gradle ends (tools/build-slots.ps1). This shell holds the slot, so a run stopped before the
@rem release still frees it when this shell ends.
@rem Decision every-gradle-run-reserves-one-of-four-slots
@rem Decision dev-instance-holds-its-slot-until-the-client-closes
@rem A run whose caller already holds a slot for it, as tools/gate.ps1 does, names that holder in BUILD_SLOT_HOLDER:
@rem the reserve answers with the slot the holder holds, and the holder frees it.
@rem Decision gate-prints-waiting-then-running-for-its-gradle-leg
set "BUILD_SLOT_RUNS=gradlew %*"
set "SLOT_HOLDER_ARGUMENTS="
if defined BUILD_SLOT_HOLDER set "SLOT_HOLDER_ARGUMENTS=-HolderPid %BUILD_SLOT_HOLDER%"
pwsh -NoProfile -File "%APP_HOME%\tools\build-slots.ps1" reserve %SLOT_HOLDER_ARGUMENTS%
if %ERRORLEVEL% neq 0 goto fail

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set GRADLE_EXIT=%ERRORLEVEL%

if not defined BUILD_SLOT_HOLDER pwsh -NoProfile -File "%APP_HOME%\tools\build-slots.ps1" release

:omega

@rem exit /b ends the local scope for the variables with windows NT shell
@exit /b %GRADLE_EXIT%

:fail
@exit /b 1
