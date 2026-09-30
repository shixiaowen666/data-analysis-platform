@echo off
cd /d "%~dp0"
echo Starting services...

:: Kill any existing processes on target ports
call :kill_port 5000
call :kill_port 5001

:: Verify ports are free
call :check_port 5000 || (pause & exit /b 1)
call :check_port 5001 || (pause & exit /b 1)

:: Start Mock System A in background
echo  [OK] Starting Mock System A on port 5001...
set PYTHONUTF8=1
start "MockSystemA" /B python -m mock_system_a.app > startup_a.log 2>&1
timeout /t 2 /nobreak >nul

:: Start System B in foreground
echo  [OK] Starting System B on port 5000...
echo.
echo  Endpoints:
echo    http://127.0.0.1:5000/test_analyze_stream
echo    http://127.0.0.1:5000/test_logs         Operation Logs Test Page
echo    http://127.0.0.1:5000/test_prompts      Prompts Management Test Page
echo    http://127.0.0.1:5000/health             Health Check  
echo.
echo  Analyze flow (via Mock System A):
echo    POST http://127.0.0.1:5001/api/v1/analyze
echo.
echo  Press Ctrl+C to stop System B, then run stop.bat to clean up both.
echo  ^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^-^
echo.
set PYTHONUTF8=1
python -m system_b.app > startup.log 2>&1

:: Cleanup: also kill System A when System B exits
call :kill_port 5001
goto :eof

:kill_port
netstat -ano > %TEMP%\netstat_port.txt 2>nul
for /f "tokens=5" %%a in ('findstr ":%1.*LISTENING" %TEMP%\netstat_port.txt 2^>nul') do (
    taskkill /pid %%a /f >nul 2>&1
)
del %TEMP%\netstat_port.txt 2>nul
goto :eof

:check_port
netstat -ano > %TEMP%\netstat_port.txt 2>nul
findstr ":%1.*LISTENING" %TEMP%\netstat_port.txt >nul 2>&1
if not errorlevel 1 (
    del %TEMP%\netstat_port.txt 2>nul
    echo  [FAIL] Port %1 still occupied, aborting.
    exit /b 1
)
del %TEMP%\netstat_port.txt 2>nul
goto :eof