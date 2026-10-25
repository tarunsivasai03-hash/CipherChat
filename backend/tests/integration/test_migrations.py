"""The checked-in migrations must build the same schema as the models."""
import os

from flask_migrate import upgrade
from sqlalchemy import create_engine, inspect

from app import create_app
from app.extensions import db


def test_migrations_match_models(tmp_path):
    migrated = (tmp_path / "migrated.db").as_posix()
    app = create_app({"SQLALCHEMY_DATABASE_URI": f"sqlite:///{migrated}"}, env="testing")
    with app.app_context():
        upgrade(directory=os.path.join(os.path.dirname(__file__), "..", "..", "migrations"))
        db.session.remove()
        db.engine.dispose()
    insp = inspect(create_engine(f"sqlite:///{migrated}"))
    migrated_tables = {t for t in insp.get_table_names() if t != "alembic_version"}
    assert migrated_tables == set(db.metadata.tables), migrated_tables ^ set(db.metadata.tables)
    for name, table in db.metadata.tables.items():
        assert {c["name"] for c in insp.get_columns(name)} == {c.name for c in table.columns}, name
