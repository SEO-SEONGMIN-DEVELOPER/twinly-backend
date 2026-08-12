import json, urllib.request, sys

BASE = "http://10.0.23.25:8080"
PHONE = "01000009999"

def call(path, body):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())

vt = call("/api/v1/auth/sms/send", {"phone": PHONE})["smsVerificationToken"]
vd = call("/api/v1/auth/sms/verify", {"smsVerificationToken": vt, "code": "000000"})["smsVerifiedToken"]
tok = call("/api/v1/auth/login", {"smsVerifiedToken": vd})
open("/tmp/token.txt", "w").write(tok["accessToken"])
print("accessExpiresAt:", tok["accessExpiresAt"])
