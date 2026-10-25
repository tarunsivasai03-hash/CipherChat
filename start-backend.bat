@echo off
rem CipherChat dev backend: double-click to start. Listens on all interfaces so a phone on the same Wi-Fi can reach it.
cd /d "%~dp0backend"
if not exist ".venv\Scripts\python.exe" (
  echo First-time setup: creating the Python environment...
  py -m venv .venv || goto :fail
  ".venv\Scripts\python.exe" -m pip install --use-feature=truststore -r requirements-dev.txt || goto :fail
)
set FLASK_APP=run.py
".venv\Scripts\python.exe" -m flask db upgrade || goto :fail
set HOST=0.0.0.0
echo.
echo ================================================================
echo  Put one of these in the app: Server settings, then Save
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do for /f "tokens=*" %%b in ("%%a") do echo     http://%%b:5000/
echo  (Use the 192.168.x.x one that matches your phone's Wi-Fi.)
echo  Or plug the phone in by USB and run  use-usb.bat  to use http://127.0.0.1:5000/
echo ================================================================
echo.
".venv\Scripts\python.exe" run.py
goto :eof
:fail
echo Setup failed. See the messages above.
pause
