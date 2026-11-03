@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------
@IF "%DEBUG%" == "1" @ECHO ON
@SETLOCAL

@SET ERROR_CODE=0

@SET MAVEN_PROJECTBASEDIR=%~dp0
@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@REM Fallback to local installed mvn if available
@WHERE mvn >nul 2>nul
@IF %ERRORLEVEL% EQU 0 (
  mvn %*
  @EXIT /B %ERRORLEVEL%
)

@IF NOT EXIST %WRAPPER_JAR% (
    @ECHO Downloading Maven Wrapper jar...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar', '%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar')"
)

@IF EXIST %WRAPPER_JAR% (
    IF DEFINED JAVA_HOME (
        "%JAVA_HOME%\bin\java" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -cp %WRAPPER_JAR% %WRAPPER_LAUNCHER% %*
    ) ELSE (
        java "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -cp %WRAPPER_JAR% %WRAPPER_LAUNCHER% %*
    )
    @IF ERRORLEVEL 1 GOTO error
    @GOTO end
) ELSE (
    @ECHO Maven wrapper jar could not be downloaded. Please install Maven or run with an active internet connection.
    @EXIT /B 1
)

:error
@SET ERROR_CODE=1

:end
@EXIT /B %ERROR_CODE%

