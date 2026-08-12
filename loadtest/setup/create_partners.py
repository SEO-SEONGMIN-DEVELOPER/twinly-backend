import json, sys, urllib.request, urllib.error

BASE = "http://10.0.23.25:8080"
START = int(sys.argv[1]) if len(sys.argv) > 1 else 1
COUNT = int(sys.argv[2]) if len(sys.argv) > 2 else 21
CODE = "000000"
AFFILIATION = "자유전공계열"


def call(method, path, body=None, token=None, expect=(200, 201)):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            raw = res.read().decode()
            if res.status not in expect:
                raise RuntimeError(f"{method} {path} -> {res.status} {raw[:200]}")
            return json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{method} {path} -> {e.code} {e.read().decode()[:300]}") from None


def create(i):
    nickname = f"loadgen-p{i:02d}"
    email = f"test-loadgen-p{i:02d}@skku.edu"
    phone = f"0100000{1000 + i:04d}"

    tok = call("POST", "/api/v1/anon/start")["anonSessionToken"]

    call("PUT", "/api/v1/onboarding/basic-info", {
        "familyName": "부하", "givenName": f"파트너{i:02d}", "gender": "male" if i % 2 else "female",
        "affiliationNumber": f"2020{100000 + i}", "birthDate": "2000-01-01",
    }, tok)

    call("PUT", "/api/v1/onboarding/profile/nickname", {"nickname": nickname}, tok)

    et = call("POST", "/api/v1/auth/onboarding/email/send", {"email": email}, tok)["emailVerificationToken"]
    call("POST", "/api/v1/auth/onboarding/email/verify", {"emailVerificationToken": et, "code": CODE}, tok)

    call("POST", "/api/v1/onboarding/affiliation", {"affiliation": AFFILIATION}, tok)

    st = call("POST", "/api/v1/auth/onboarding/sms/send", {"phone": phone}, tok)["smsVerificationToken"]
    call("POST", "/api/v1/auth/onboarding/sms/verify", {"smsVerificationToken": st, "code": CODE}, tok)

    call("POST", "/api/v1/auth/signup", None, tok)
    return {"nickname": nickname, "email": email, "phone": phone}


created, failed = [], []
for i in range(START, COUNT + 1):
    try:
        created.append(create(i))
        print(f"  [{i:02d}/{COUNT}] ok  loadgen-p{i:02d}", flush=True)
    except Exception as e:
        failed.append((i, str(e)))
        print(f"  [{i:02d}/{COUNT}] FAIL {e}", flush=True)

print(f"\n생성 {len(created)}건 / 실패 {len(failed)}건")
open(f"/home/ubuntu/loadtest/partners-{START}-{COUNT}.json", "w").write(json.dumps(created, ensure_ascii=False, indent=2))
