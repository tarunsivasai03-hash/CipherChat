"""Development entry point. Do NOT use in production (see docs/DEPLOYMENT.md)."""
import os

from app import create_app
from app.extensions import socketio

app = create_app()

if __name__ == "__main__":
    if app.config["APP_ENV"] == "production":
        raise SystemExit("Refusing to start the development server in production; use gunicorn (see docs/DEPLOYMENT.md).")
    host = os.environ.get("HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "5000"))
    # allow_unsafe_werkzeug: development only.
    socketio.run(app, host=host, port=port, allow_unsafe_werkzeug=True)
