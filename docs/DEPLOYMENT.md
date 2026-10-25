# Deployment guide

> A release build / passing tests is **not** a security audit and **not** a Play Store-ready product.

## Topology (initial)

```
Android app ──HTTPS/WSS──▶ TLS-terminating reverse proxy (nginx/Caddy) ──HTTP──▶ gunicorn (1 process, many threads)
                                                                                      └─▶ SQLite on a persistent volume
```

## Configuration (environment variables)

Copy `backend/.env.example` to `backend/.env` (never commit it). Production **refuses to start** unless:

* `APP_ENV=production`
* `SECRET_KEY` is ≥32 random chars: `python -c "import secrets; print(secrets.token_hex(32))"`
* `DATABASE_URL` is explicitly set (SQLite path on a persistent volume, or PostgreSQL)
* `REQUIRE_HTTPS=true` (default in production): non-HTTPS requests get `400 https_required`; HSTS is sent
* `SOCKETIO_CORS_ORIGINS` does not contain `*`

Set `TRUSTED_PROXY_COUNT=1` when behind exactly one proxy, so client IPs (rate limiting) and the HTTPS scheme come
from `X-Forwarded-*`. Do not set it when the app is directly exposed (it would let clients spoof their IP).

Secrets live in the environment or your platform's secret manager. **Nothing secret is embedded in the APK.**

## Database and migrations

```bash
cd backend && source .venv/bin/activate   # Windows: .\.venv\Scripts\Activate.ps1
export FLASK_APP=run.py                    # PowerShell: $env:FLASK_APP="run.py"
flask db upgrade                           # applies migrations/versions/*
```

Changing models: `flask db migrate -m "describe change"`, **review the generated script**, test it on a copy
of production data, then `flask db upgrade`. Back up first.

SQLite notes: WAL mode is enabled; it supports one writer at a time, which is fine for a small deployment but
not for heavy concurrent writes. Put the file on a persistent volume (not a container layer), back it up with
`sqlite3 cipherchat.db ".backup 'backup.db'"` (not by copying a live file), and encrypt/restrict the backup because it
contains all server-readable messages. Move to PostgreSQL (`DATABASE_URL=postgresql+psycopg://…`, `pip install
psycopg[binary]`) when you need concurrency, replication or managed backups; the schema is portable but **test the
migrations on PostgreSQL first**, they have only been run on SQLite here.

Retention: nothing is kept past deletion except what's in your backups. Schedule disappearing-message cleanup:

```cron
*/5 * * * * cd /srv/cipherchat/backend && FLASK_APP=run.py .venv/bin/flask purge-expired
```

(Expired messages are never *served* even without this job; it physically removes them.)

## Running the server

Development only: `python run.py` (refuses to run when `APP_ENV=production`).

Production (Linux), one worker with threads, which Flask-SocketIO supports through `simple-websocket`:

```bash
gunicorn --workers 1 --threads 100 --bind 127.0.0.1:8000 run:app
```

Keep **one worker**: the socket registry and socket rate-limit windows are in process memory. To scale out you need a
Socket.IO message queue (Redis), sticky sessions and moving that state to Redis. This has **not** been built or tested.
`gunicorn` does not run on Windows; use WSL/Linux/a container for production.
**The gunicorn command and nginx sketch were written from the Flask-SocketIO docs but could not be executed here (Windows dev machine); the app itself was verified with the development server only. Test them in your target environment before relying on them.**

Rate limiting uses in-memory storage (`RATELIMIT_STORAGE_URI=memory://`), so limits reset on restart and are per
process. Use Redis storage if you add workers.

### nginx sketch

```nginx
server {
  listen 443 ssl http2;
  server_name api.example.com;
  # ssl_certificate / ssl_certificate_key via your CA (e.g. certbot)
  location / {
    proxy_pass http://127.0.0.1:8000;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 75s;
    client_max_body_size 64k;
  }
}
```

## Hardening checklist

* HTTPS only; modern TLS config; HSTS (the app sends it).
* Restrict network access: only the proxy reaches gunicorn; the DB volume isn't exposed.
* Run as an unprivileged user; read-only code directory; writable only the DB volume.
* Logging: app logs avoid bodies and tokens; the access log (and nginx) contain IP addresses and URL paths
  including the `q=` of user search. Set retention accordingly and restrict access.
* Monitoring: alert on `/api/v1/health`, 5xx rate, 429 spikes, disk space.
* Dependencies: `pip list --outdated`, `pip-audit` (not run here) before each release.
* Test restores of backups. Test migrations before deploying.

## Android release builds

1. Set the production API origin: `android/app/build.gradle.kts` → `release { buildConfigField("String","DEFAULT_BASE_URL","\"https://api.example.com/\"") }`.
   Release builds only accept `https://` (the debug-only cleartext network config is not in release).
2. Create a keystore **outside the repo** and an untracked `android/keystore.properties`:
   ```
   storeFile=../../secrets/cipherchat-release.jks
   storePassword=…
   keyAlias=cipherchat
   keyPassword=…
   ```
   (`*.jks`, `*.keystore`, `keystore.properties` are in `android/.gitignore`.)
3. `./gradlew assembleRelease` (APK) or `./gradlew bundleRelease` (AAB for Play). Without `keystore.properties` the
   release APK is produced **unsigned**.
4. Bump `versionCode`/`versionName` in `app/build.gradle.kts` for each release.
5. Before publishing: a real security review/pen-test, privacy policy review by counsel, Play Data Safety form,
   target-API and policy checks, crash reporting decision, store assets. None of this has been done.
