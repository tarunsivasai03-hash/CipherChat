from app.security import passwords
from app.security.auth import hash_token, new_token


def test_hash_is_argon2id_and_not_plaintext():
    h = passwords.hash_password("a-long-test-password")
    assert h.startswith("$argon2id$")
    assert "a-long-test-password" not in h


def test_verify_roundtrip_and_mismatch():
    h = passwords.hash_password("a-long-test-password")
    assert passwords.verify_password(h, "a-long-test-password")
    assert not passwords.verify_password(h, "wrong-password-123")
    assert not passwords.verify_password("not-a-hash", "x")


def test_hashes_are_salted():
    assert passwords.hash_password("same-password-12") != passwords.hash_password("same-password-12")


def test_token_entropy_and_hashing():
    t1, h1 = new_token()
    t2, h2 = new_token()
    assert t1 != t2 and h1 != h2
    assert len(t1) >= 43  # 256 bits, base64url
    assert h1 == hash_token(t1) and t1 not in h1
