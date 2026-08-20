@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo =========================================
echo SmartHelp startup launcher
echo =========================================

echo.
where docker >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [1/3] Starting MySQL with Docker...
    docker compose up -d mysql
    if %ERRORLEVEL% NEQ 0 (
        echo Docker MySQL startup failed. If you are using a local MySQL install, continue manually.
    )
) else (
    echo Docker not found. Skipping Docker MySQL startup.
)

echo.
where mysql >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [2/3] Checking MySQL access and loading database...

    mysql -h 127.0.0.1 -P 3306 -u root -e "SELECT 1;" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo Using local MySQL root account with no password.
        mysql -h 127.0.0.1 -P 3306 -u root < "%ROOT%database\schema.sql"
        if %ERRORLEVEL% EQU 0 (
            mysql -h 127.0.0.1 -P 3306 -u root < "%ROOT%database\seed.sql"
        )
    ) else (
        mysql -h 127.0.0.1 -P 3306 -u smarthelp -psmarthelp -e "SELECT 1;" >nul 2>&1
        if %ERRORLEVEL% EQU 0 (
            echo Using smarthelp user credentials.
            mysql -h 127.0.0.1 -P 3306 -u smarthelp -psmarthelp < "%ROOT%database\schema.sql"
            if %ERRORLEVEL% EQU 0 (
                mysql -h 127.0.0.1 -P 3306 -u smarthelp -psmarthelp < "%ROOT%database\seed.sql"
            )
        ) else (
            echo MySQL is not ready or the root password is not blank.
            echo Open MySQL and run these commands manually:
            echo   mysql -h 127.0.0.1 -P 3306 -u root
            echo   source database/dev-user.sql
            echo   source database/schema.sql
            echo   source database/seed.sql
        )
    )
) else (
    echo MySQL client not found. If using Docker, the database may already be running.
    echo If using a local MySQL install, make sure you load the SQL files manually.
)

echo.
set "JDK_PATH="
if defined JAVA_HOME (
    set "JDK_PATH=%JAVA_HOME%"
)
if not defined JDK_PATH (
    for %%D in ("C:\Program Files\Eclipse Adoptium" "C:\Program Files\Microsoft" "C:\Program Files\Java") do (
        if exist "%%~D" (
            for /d %%J in ("%%~D\jdk-*" "%%~D\*jdk*" "%%~D\Java\jdk-*") do (
                if exist "%%~J\bin\java.exe" (
                    set "JDK_PATH=%%~J"
                    goto :found_jdk
                )
            )
        )
    )
)
:found_jdk
if not defined JDK_PATH (
    echo JAVA_HOME is not set and no Java 21 JDK was found.
    echo Install Java 21 from Adoptium or Microsoft Build of OpenJDK.
    echo Then run:
    echo   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
    echo   .\start-smarthelp.bat
    pause
    exit /b 1
)

echo Using Java from: %JDK_PATH%
set "JAVA_HOME=%JDK_PATH%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [3/3] Starting backend, AI service and frontend...

start "SmartHelp Backend" cmd /k "cd /d ""%ROOT%backend"" && set JAVA_HOME=%JAVA_HOME% && set PATH=%JAVA_HOME%\bin;%PATH% && .\mvnw.cmd spring-boot:run"

if not exist "%ROOT%ai-service\.venv" (
    echo Creating Python virtual environment for AI service...
    python -m venv "%ROOT%ai-service\.venv"
)

start "SmartHelp AI" cmd /k "cd /d ""%ROOT%ai-service"" && call .venv\Scripts\activate.bat && python -m pip install -r requirements.txt && uvicorn main:app --host 127.0.0.1 --port 8000 --reload"

start "SmartHelp Frontend" cmd /k "cd /d ""%ROOT%frontend"" && npm install && npm start"

echo.
echo SmartHelp startup launched.
echo Frontend: http://localhost:4200
echo Backend:  http://localhost:8080
echo AI:      http://localhost:8000
echo.
echo Each service is running in its own terminal window.
echo If one service fails, check that terminal for the error.
echo.
pause >nul
