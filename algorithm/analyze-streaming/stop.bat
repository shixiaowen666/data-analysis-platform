@echo off
echo Stopping services...

call :kill_port 5000
call :kill_port 5001

:: Verify
call :check_port 5000
call :check_port 5001
pause
goto :eof

:kill_port
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%1.*LISTENING"') do (
    taskkill /pid %%a /f >nul 2>&1
)
goto :eof

:check_port
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%1.*LISTENING"') do (
    set FOUND=1
)
if %FOUND%==0 (
    echo  [OK] Port %1 freed.
) else (
    echo  [WARN] Port %1 still occupied, may need manual cleanup.
)
goto :eof