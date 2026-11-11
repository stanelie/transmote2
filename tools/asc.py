#!/usr/bin/env python3
"""
Minimal App Store Connect API client: enough to wait for a build to finish
processing and hand it to a TestFlight group.

No pip dependencies - a JWT signed with the .p8 key via the openssl binary,
the same approach as server/transmote_push.py.

    asc.py app-id          --bundle-id ca.exmachina.transmote2
    asc.py wait-for-build  --build 7
    asc.py attach          --build 7
"""

import argparse
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

API = "https://api.appstoreconnect.apple.com/v1"
KEY_DIR = os.path.expanduser("~/.appstoreconnect/private_keys")


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def der_to_jose(der: bytes) -> bytes:
    """openssl emits DER SEQUENCE{INTEGER r, INTEGER s}; JWS wants r||s, each
    left-padded to 32 bytes."""
    if der[0] != 0x30:
        raise ValueError("not a DER sequence")
    i = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)

    def read_int(pos):
        if der[pos] != 0x02:
            raise ValueError("expected DER INTEGER")
        ln = der[pos + 1]
        return der[pos + 2: pos + 2 + ln].lstrip(b"\x00").rjust(32, b"\x00"), pos + 2 + ln

    r, i = read_int(i)
    s, _ = read_int(i)
    return r + s


def jwt(key_id: str, issuer: str) -> str:
    key = os.path.join(KEY_DIR, f"AuthKey_{key_id}.p8")
    if not os.path.exists(key):
        sys.exit(f"no such key: {key}")
    now = int(time.time())
    header = b64url(json.dumps({"alg": "ES256", "kid": key_id, "typ": "JWT"}).encode())
    # 20 minutes; Apple rejects anything over 20.
    claims = b64url(json.dumps({
        "iss": issuer, "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1",
    }).encode())
    signing_input = f"{header}.{claims}".encode()
    proc = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key],
                          input=signing_input, capture_output=True)
    if proc.returncode != 0:
        sys.exit(f"openssl failed: {proc.stderr.decode()}")
    return f"{header}.{claims}.{b64url(der_to_jose(proc.stdout))}"


def call(args, path, method="GET", body=None):
    req = urllib.request.Request(
        f"{API}{path}", method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": f"Bearer {jwt(args.key_id, args.issuer)}",
                 "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"raw": raw.decode(errors="replace")}


def errors(payload) -> str:
    if isinstance(payload, dict) and payload.get("errors"):
        return "; ".join(e.get("detail", e.get("title", "?")) for e in payload["errors"])
    return json.dumps(payload)[:400]


def find_build(args, version):
    status, payload = call(
        args, f"/builds?filter[app]={args.app}&limit=20&sort=-uploadedDate")
    if status != 200:
        sys.exit(f"listing builds failed (HTTP {status}): {errors(payload)}")
    for b in payload.get("data", []):
        if b["attributes"].get("version") == str(version):
            return b
    return None


def cmd_app_id(args):
    status, payload = call(args, f"/apps?filter[bundleId]={args.bundle_id}")
    if status != 200:
        sys.exit(f"HTTP {status}: {errors(payload)}")
    for a in payload.get("data", []):
        print(a["id"], a["attributes"]["name"])


def cmd_wait(args):
    deadline = time.time() + args.timeout
    while True:
        build = find_build(args, args.build)
        if build:
            state = build["attributes"].get("processingState")
            if state == "VALID":
                print(build["id"])
                return 0
            if state in ("INVALID", "FAILED"):
                sys.exit(f"build {args.build} came back {state}")
            print(f"build {args.build}: {state}", file=sys.stderr)
        else:
            print(f"build {args.build}: not listed yet", file=sys.stderr)
        if time.time() > deadline:
            sys.exit(f"build {args.build} still not VALID after {args.timeout}s")
        time.sleep(30)


def cmd_attach(args):
    build = find_build(args, args.build)
    if not build:
        sys.exit(f"build {args.build} is not listed")

    # A build cannot go to testers until export compliance is answered. The
    # ITSAppUsesNonExemptEncryption key in Info.plist answers it at upload
    # time; without it the attach fails with an unhelpful "not in an
    # internally testable state", so say what is actually wrong.
    if build["attributes"].get("usesNonExemptEncryption") is None:
        sys.exit(f"build {args.build} has no export compliance answer. Either set\n"
                 f"ITSAppUsesNonExemptEncryption in iosApp/iosApp/Info.plist and\n"
                 f"upload again, or answer it under TestFlight in App Store Connect.")

    status, payload = call(args, f"/betaGroups/{args.group}/relationships/builds",
                           method="POST",
                           body={"data": [{"type": "builds", "id": build["id"]}]})
    if status not in (200, 204):
        sys.exit(f"attach failed (HTTP {status}): {errors(payload)}")
    print(f"build {args.build} attached to group {args.group}")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key-id", default=os.environ.get("ASC_KEY_ID"))
    ap.add_argument("--issuer", default=os.environ.get("ASC_ISSUER_ID"))
    ap.add_argument("--app", default=os.environ.get("ASC_APP_ID"))
    ap.add_argument("--group", default=os.environ.get("ASC_GROUP_ID"))
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("app-id"); p.add_argument("--bundle-id", required=True)
    p.set_defaults(fn=cmd_app_id)
    p = sub.add_parser("wait-for-build")
    p.add_argument("--build", required=True)
    p.add_argument("--timeout", type=int, default=2400)
    p.set_defaults(fn=cmd_wait)
    p = sub.add_parser("attach"); p.add_argument("--build", required=True)
    p.set_defaults(fn=cmd_attach)

    args = ap.parse_args()
    if not args.key_id or not args.issuer:
        sys.exit("set ASC_KEY_ID and ASC_ISSUER_ID (see tools/release.conf.example)")
    sys.exit(args.fn(args) or 0)


if __name__ == "__main__":
    main()
