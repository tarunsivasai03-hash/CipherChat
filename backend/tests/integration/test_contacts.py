def test_search_prefix_min_length_and_exclusions(make_user):
    a, b, c = make_user("search_me_a"), make_user("searchable_b"), make_user("hidden_search_c")
    c.patch("/api/v1/settings", {"discoverable": False})
    assert a.get("/api/v1/users/search?q=se").status_code == 400
    names = [u["username"] for u in a.get("/api/v1/users/search?q=search").get_json()["users"]]
    assert "searchable_b" in names and "search_me_a" not in names  # excludes self
    assert a.get("/api/v1/users/search?q=hidden").get_json()["users"] == []  # non-discoverable
    assert a.get("/api/v1/users/search?q=%25%25%25").get_json()["users"] == []  # LIKE wildcards escaped


def test_request_accept_creates_symmetric_contacts(make_user, befriend):
    a, b = make_user(), make_user()
    befriend(a, b)
    assert [u["id"] for u in a.get("/api/v1/contacts").get_json()["contacts"]] == [b.id]
    assert [u["id"] for u in b.get("/api/v1/contacts").get_json()["contacts"]] == [a.id]


def test_reject_and_resend(make_user):
    a, b = make_user(), make_user()
    a.post("/api/v1/contact-requests", {"user_id": b.id})
    rid = b.get("/api/v1/contact-requests").get_json()["incoming"][0]["id"]
    assert b.post(f"/api/v1/contact-requests/{rid}/reject").status_code == 200
    assert b.get("/api/v1/contacts").get_json()["contacts"] == []
    a.post("/api/v1/contact-requests", {"user_id": b.id})
    assert len(b.get("/api/v1/contact-requests").get_json()["incoming"]) == 1


def test_only_recipient_can_answer_request(make_user):
    a, b, c = make_user(), make_user(), make_user()
    a.post("/api/v1/contact-requests", {"user_id": b.id})
    rid = b.get("/api/v1/contact-requests").get_json()["incoming"][0]["id"]
    assert a.post(f"/api/v1/contact-requests/{rid}/accept").status_code == 404  # sender cannot self-accept
    assert c.post(f"/api/v1/contact-requests/{rid}/accept").status_code == 404


def test_request_response_does_not_reveal_existence(make_user):
    a, b = make_user(), make_user()
    real = a.post("/api/v1/contact-requests", {"user_id": b.id})
    ghost = a.post("/api/v1/contact-requests", {"user_id": 999999})
    ghost_name = a.post("/api/v1/contact-requests", {"username": "does_not_exist"})
    assert real.status_code == ghost.status_code == ghost_name.status_code == 202
    assert real.get_json() == ghost.get_json() == ghost_name.get_json()


def test_mutual_request_auto_accepts(make_user):
    a, b = make_user(), make_user()
    a.post("/api/v1/contact-requests", {"user_id": b.id})
    b.post("/api/v1/contact-requests", {"user_id": a.id})
    assert len(a.get("/api/v1/contacts").get_json()["contacts"]) == 1


def test_remove_contact(make_user, befriend):
    a, b = make_user(), make_user()
    befriend(a, b)
    assert a.delete(f"/api/v1/contacts/{b.id}").status_code == 204
    assert b.get("/api/v1/contacts").get_json()["contacts"] == []


def test_block_removes_contact_and_hides_from_search_and_requests(make_user, befriend):
    a, b = make_user("blocker_aa"), make_user("blocked_bb")
    befriend(a, b)
    r = a.post("/api/v1/blocks", {"user_id": b.id})
    assert r.status_code == 201
    assert a.get("/api/v1/contacts").get_json()["contacts"] == []
    assert b.get("/api/v1/contacts").get_json()["contacts"] == []
    assert b.get("/api/v1/users/search?q=blocker").get_json()["users"] == []
    assert b.post("/api/v1/contact-requests", {"user_id": a.id}).status_code == 202  # silently dropped
    assert a.get("/api/v1/contact-requests").get_json()["incoming"] == []
    assert a.post("/api/v1/contact-requests", {"user_id": b.id}).status_code == 409  # must unblock first
    assert a.delete(f"/api/v1/blocks/{r.get_json()['id']}").status_code == 204


def test_cannot_unblock_someone_elses_block(make_user):
    a, b, c = make_user(), make_user(), make_user()
    bid = a.post("/api/v1/blocks", {"user_id": b.id}).get_json()["id"]
    assert c.delete(f"/api/v1/blocks/{bid}").status_code == 404
    assert b.delete(f"/api/v1/blocks/{bid}").status_code == 404


def test_self_actions_rejected(make_user):
    a = make_user()
    assert a.post("/api/v1/contact-requests", {"user_id": a.id}).status_code == 400
    assert a.post("/api/v1/blocks", {"user_id": a.id}).status_code == 400
