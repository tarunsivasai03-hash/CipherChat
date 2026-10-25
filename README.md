# CipherChat

Native Android messenger with two clearly separated modes:

* **Secret Cipher** – educational ciphers (Caesar, Vigenère, ROT13, substitution, custom alphabet, emoji codes). Fun, **not secure**.
* **Private Chat (E2E)** – **not available yet.** No end-to-end protocol is integrated; the app and server say so everywhere
  and never show an "encrypted" badge. See [docs/E2E_STATUS.md](docs/E2E_STATUS.md) for why (AGPLv3 licence decision needed) and what remains.

Chats today are normal server-readable chats over HTTPS: accounts, contacts/requests/blocks, direct and group chats,
realtime delivery, delivery receipts (no read receipts), replies, reactions, disappearing messages, offline queue,
app lock, device/session management, account deletion.

| Folder | What |
|---|---|
| `android/` | Kotlin · Compose · Material 3 · Room · DataStore · Retrofit · Socket.IO |
| `backend/` | Flask · Flask-SocketIO · SQLAlchemy · Flask-Migrate · Argon2id |
| `docs/` | [ARCHITECTURE](docs/ARCHITECTURE.md) · [API](docs/API.md) · [SECURITY](docs/SECURITY.md) · [E2E_STATUS](docs/E2E_STATUS.md) · [DEPLOYMENT](docs/DEPLOYMENT.md) · [PRIVACY_POLICY (draft)](docs/PRIVACY_POLICY.md) · [CHECKLIST](docs/CHECKLIST.md) |

## Backend setup

Windows PowerShell:
```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements-dev.txt      # use requirements.txt for production
Copy-Item .env.example .env              # then set SECRET_KEY (see below)
$env:FLASK_APP = "run.py"
flask db upgrade
python run.py                            # http://127.0.0.1:5000 (dev server only)
```
Linux/macOS:
```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements-dev.txt
cp .env.example .env
export FLASK_APP=run.py
flask db upgrade
python run.py
```
Generate a development secret and put it in `.env`:
`python -c "import secrets; print(secrets.token_hex(32))"`. Never commit `.env`. (In development an ephemeral key is
generated if unset.) For devices/emulators to reach the server run it with `HOST=0.0.0.0` (firewall permitting).

Health check: `GET /api/v1/health`. Tests: `python -m pytest` (78 tests, isolated temp databases).
Production: **do not use `run.py`** → [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

### Easiest way to run (Windows)
* **`start-backend.bat`**: double-click. Creates the Python environment on first run, applies migrations, starts the server on all
  interfaces and prints the addresses to type into the app (*Server settings*).
* **`use-usb.bat`**: with the phone plugged in (USB debugging on), run it, then set the app's server to `http://127.0.0.1:5000/`.
  No Wi-Fi, IP address or firewall rule needed, and the address never changes. Re-run it after replugging the phone.

## Android setup

Requirements: Android Studio (or just the SDK), **JDK 17**, Android SDK platform 36 + build-tools. The project uses
AGP 8.13.2, Gradle 8.14.5 (wrapper), Kotlin 2.2.21, Compose BOM 2025.10.01, Room 2.8.5, Retrofit 2.12.0, OkHttp 4.12.0,
socket.io-client 2.1.2, `compileSdk/targetSdk 36`, `minSdk 26` (see `android/gradle/libs.versions.toml`).
Newer Compose/Navigation/Lifecycle releases require AGP 9 / compileSdk 37 and were deliberately not used.

1. Open `android/` in Android Studio and let Gradle sync (or use the commands below). Create `android/local.properties`
   with `sdk.dir=C:/Users/you/AppData/Local/Android/Sdk` if Studio hasn't.
2. Start the backend (`HOST=0.0.0.0` for devices).
3. **Emulator:** the host machine is `http://10.0.2.2:5000/` (the debug default; `localhost` inside the emulator is the
   emulator itself). **Physical device:** *Server settings* → `http://<your-PC-LAN-IP>:5000/`, same Wi-Fi, firewall open for port 5000.
   Cleartext HTTP exists in **debug builds only**; release builds require `https://`. TLS verification is never disabled.
4. Run the `app` configuration.

Commands (run in `android/`; set `JAVA_HOME` to a JDK 17):
```bash
./gradlew testDebugUnitTest        # 69 JVM tests incl. Compose UI tests (Robolectric)
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # unsigned unless keystore.properties exists
./gradlew connectedDebugAndroidTest   # needs an emulator/device
```
Windows: `gradlew.bat testDebugUnitTest`, `gradlew.bat assembleDebug`.

Troubleshooting: if you're behind TLS-intercepting software and Gradle/pip report certificate errors, make the tools
trust the OS certificate store rather than disabling verification: `pip install --use-feature=truststore …`, and
`JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` for Gradle on Windows.
`IllegalArgumentException: 25.0.3` at sync (or any Kotlin-DSL `JavaVersion.parse` crash): Gradle 8.14 can't run on JDK 25
(Android Studio's newest bundled JBR). The project pins the Gradle daemon to JDK 17 via `android/gradle/gradle-daemon-jvm.properties`,
which needs a JDK 17 installed. Also set *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK* to a
17 JDK (Studio can download one: *Download JDK… → 17*), then re-sync.
On low-RAM machines (≤8 GB) stop the Gradle daemon (`./gradlew --stop`) before running an emulator.

## Status

See [docs/CHECKLIST.md](docs/CHECKLIST.md) for exactly what is done, verified, and not done.
This is an MVP that has **not** had a security audit and is **not** Play-Store ready.
