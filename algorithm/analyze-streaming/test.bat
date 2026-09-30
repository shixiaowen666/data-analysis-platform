@echo off
chcp 936 >nul 2>&1
setlocal

>nul 2>&1 net session || (
    echo Requesting admin rights...
    powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

set "RPT=%~dp0DiagReport_%COMPUTERNAME%.txt"

title Laptop Slowdown Diagnostic
cls
echo.
echo   ============================================================
echo     Laptop Slowdown Diagnostic Tool
echo     Collecting info, please wait 1-2 minutes...
echo     Report: %RPT%
echo   ============================================================
echo.

> "%RPT%" echo ==================================================
>>"%RPT%" echo   Laptop Diagnostic Report
>>"%RPT%" echo   Time: %date% %time%
>>"%RPT%" echo ==================================================

call :sec "1. System and Hardware Overview"
call :ps "$os=Get-CimInstance Win32_OperatingSystem;$cs=Get-CimInstance Win32_ComputerSystem;$c=Get-CimInstance Win32_Processor|Select-Object -First 1;'Computer : '+$env:COMPUTERNAME;'Vendor   : '+$cs.Manufacturer+' '+$cs.Model;'OS       : '+$os.Caption+' '+$os.Version;'CPU      : '+$c.Name;'Cores    : '+$c.NumberOfCores+' / '+$c.NumberOfLogicalProcessors+' threads';'RAM      : '+[math]::Round($cs.TotalPhysicalMemory/1GB,2)+' GB';'LastBoot : '+$os.LastBootUpTime;'Uptime   : '+[math]::Round(((Get-Date)-$os.LastBootUpTime).TotalHours,1)+' h'"

call :sec "2. CPU and Memory Usage"
call :ps "'CPU samples (every 0.5s):';(1..6|ForEach-Object{(Get-CimInstance Win32_Processor|Measure-Object -Property LoadPercentage -Average).Average;Start-Sleep -Milliseconds 500})-join '  ->  '"
call :ps "$o=Get-CimInstance Win32_OperatingSystem;$t=$o.TotalVisibleMemorySize/1MB;$f=$o.FreePhysicalMemory/1MB;'Total : '+[math]::Round($t,2)+' GB';'Used  : '+[math]::Round($t-$f,2)+' GB';'Free  : '+[math]::Round($f,2)+' GB';'Usage : '+[math]::Round(($t-$f)/$t*100,1)+' %'"

call :sec "3. Disk Space"
call :ps "Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3'|ForEach-Object{'  '+$_.DeviceID+'  Total '+[math]::Round($_.Size/1GB,1)+' GB  Free '+[math]::Round($_.FreeSpace/1GB,1)+' GB  Free% '+[math]::Round($_.FreeSpace/$_.Size*100,1)}"

call :sec "4. Disk Type and Health"
call :ps "if(Get-Command Get-PhysicalDisk -ErrorAction SilentlyContinue){Get-PhysicalDisk|ForEach-Object{'  Disk'+$_.DeviceId+'  Type:'+$_.MediaType+'  Bus:'+$_.BusType+'  Health:'+$_.HealthStatus+'  Size:'+[math]::Round($_.Size/1GB,0)+' GB'}}else{'Not supported, use CrystalDiskInfo'}"

call :sec "5. Top 10 Memory Consumers"
call :ps "Get-Process|Sort-Object WorkingSet64 -Descending|Select-Object -First 10 @{n='Name';e={$_.Name}},@{n='PID';e={$_.Id}},@{n='MemMB';e={[math]::Round($_.WorkingSet64/1MB,0)}}|Format-Table -AutoSize|Out-String -Width 120"

call :sec "6. Top 10 CPU Consumers"
call :ps "Get-Process|Sort-Object CPU -Descending|Select-Object -First 10 @{n='Name';e={$_.Name}},@{n='PID';e={$_.Id}},@{n='CPUs';e={[math]::Round($_.CPU,0)}},@{n='MemMB';e={[math]::Round($_.WorkingSet64/1MB,0)}}|Format-Table -AutoSize|Out-String -Width 120"

call :sec "7. Startup Items"
call :ps "Get-CimInstance Win32_StartupCommand|Select-Object Name,Location|Format-Table -AutoSize|Out-String -Width 160"

call :sec "8. Active Power Plan"
for /f "tokens=*" %%i in ('powercfg /getactivescheme') do call :out "%%i"

call :sec "9. Problem Devices"
call :ps "$d=Get-CimInstance Win32_PnPEntity|Where-Object{$_.ConfigManagerErrorCode -ne 0};if($d){$d|Select-Object Name,ConfigManagerErrorCode|Format-Table -AutoSize|Out-String -Width 160}else{'No problem devices found'}"

call :sec "10. System Error Logs (last 7 days, max 15)"
call :ps "Get-WinEvent -FilterHashtable @{LogName='System';Level=@(1,2);StartTime=(Get-Date).AddDays(-7)} -MaxEvents 15 -ErrorAction SilentlyContinue|Select-Object TimeCreated,Id,ProviderName|Format-Table -AutoSize|Out-String -Width 160"

call :sec "Common Causes Checklist"
call :out " 1) Memory usage > 85%          - Add RAM or close resident apps"
call :out " 2) System drive free < 10%     - Clean up disk"
call :out " 3) Disk type = HDD             - Upgrade to SSD"
call :out " 4) Disk health != Healthy      - Backup data ASAP"
call :out " 5) Startup items > 15          - Disable unneeded in Task Manager"
call :out " 6) Power plan = Power Saver    - Switch to Balanced/High perf"
call :out " 7) Many error logs             - Update GPU/chipset/NIC drivers"
call :out " 8) Problem devices present     - Reinstall their drivers"
call :out " 9) All above OK but still slow - Thermal issue, clean dust/paste"

echo.
echo   ============================================================
echo     Done! Report saved to:
echo     %RPT%
echo   ============================================================
echo.
start "" notepad "%RPT%"
pause
exit /b

:sec
echo.
echo ------------------------------------------------------------
echo  %~1
echo ------------------------------------------------------------
>>"%RPT%" echo.
>>"%RPT%" echo ------------------------------------------------------------
>>"%RPT%" echo  %~1
>>"%RPT%" echo ------------------------------------------------------------
exit /b

:ps
for /f "usebackq delims=" %%i in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "[Console]::OutputEncoding=[System.Text.Encoding]::GetEncoding(936); %~1"`) do call :out "%%i"
exit /b

:out
echo(%~1
>>"%RPT%" echo(%~1
exit /b