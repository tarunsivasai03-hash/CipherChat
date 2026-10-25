from app.routes.account import bp as account_bp
from app.routes.auth import bp as auth_bp
from app.routes.conversations import bp as conversations_bp
from app.routes.health import bp as health_bp
from app.routes.social import bp as social_bp


def register_blueprints(app) -> None:
    for bp in (health_bp, auth_bp, social_bp, conversations_bp, account_bp):
        app.register_blueprint(bp)
