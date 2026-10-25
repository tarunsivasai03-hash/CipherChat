@echo off
rem Lets a USB-connected phone reach the backend as http://127.0.0.1:5000/ , no Wi-Fi, IP address or firewall needed.
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" devices
"%ADB%" reverse tcp:5000 tcp:5000 && echo.&& echo Done. In the app: Server settings, then enter  http://127.0.0.1:5000/  and Save.
echo (Run this again after you unplug and replug the phone.)
pause
