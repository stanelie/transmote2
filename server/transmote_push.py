#!/usr/bin/env python3
"""
Send an APNs push to the Transmote iOS app.

Talks to Apple directly: a JWT signed with the .p8 auth key, over HTTP/2.
No third-party push service, and no pip dependencies - only the Python
standard library plus the openssl and curl binaries, which any server
running Transmission already has.

Config: transmote_push.conf beside this script (see the .example).
Device tokens come from devices.json, written by register_server.py, and
fall back to the conf's [device] tokens if that file is absent.

    transmote_push.py --title "Download complete" --body "Name.Of.Torrent"
    transmote_push.py --title "New in RSS" --body "Episode" --screen rss
    transmote_push.py --silent          # wake the app to check for itself
"""

import argparse
import base64
import configparser
import json
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
CONF = os.path.join(HERE, "transmote_push.conf")
DEVICES = os.path.join(HERE, "devices.json")
JWT_CACHE = os.path.join(HERE, ".apns_jwt")

HOSTS = {
    "development": "https://api.sandbox.push.apple.com",
    "production": "https://api.push.apple.com",
}


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def der_to_jose(der: bytes) -> bytes:
    """ES256 signatures come out of openssl as DER SEQUENCE{INTEGER r, INTEGER s};
    JWS wants raw r||s, each left-padded to 32 bytes."""
    if der[0] != 0x30:
        raise ValueError("not a DER sequence")
    i = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)

    def read_int(pos):
        if der[pos] != 0x02:
            raise ValueError("expected DER INTEGER")
        ln = der[pos + 1]
        val = der[pos + 2: pos + 2 + ln].lstrip(b"\x00")
        return val.rjust(32, b"\x00"), pos + 2 + ln

    r, i = read_int(i)
    s, _ = read_int(i)
    return r + s


def make_jwt(apns):
    """APNs provider token. Apple rejects tokens regenerated more often than
    every 20 minutes, so it is cached on disk for 30."""
    if os.path.exists(JWT_CACHE) and time.time() - os.path.getmtime(JWT_CACHE) < 1800:
        with open(JWT_CACHE) as fh:
            cached = fh.read().strip()
        if cached:
            return cached

    header = b64url(json.dumps({"alg": "ES256", "kid": apns["key_id"]},
                               separators=(",", ":")).encode())
    payload = b64url(json.dumps({"iss": apns["team_id"], "iat": int(time.time())},
                                separators=(",", ":")).encode())
    signing_input = f"{header}.{payload}".encode()

    proc = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", apns["key_file"]],
        input=signing_input, capture_output=True,
    )
    if proc.returncode != 0:
        sys.exit(f"openssl could not sign with {apns['key_file']}: "
                 f"{proc.stderr.decode().strip()}")

    token = f"{header}.{payload}.{b64url(der_to_jose(proc.stdout))}"
    with open(JWT_CACHE, "w") as fh:
        fh.write(token)
    os.chmod(JWT_CACHE, 0o600)
    return token


def load():
    if not os.path.exists(CONF):
        sys.exit(f"missing config: {CONF}")
    cp = configparser.ConfigParser()
    cp.read(CONF)
    apns = cp["apns"]

    tokens = []
    if os.path.exists(DEVICES):
        with open(DEVICES) as fh:
            tokens = [d["token"] for d in json.load(fh).values() if d.get("token")]
    if not tokens and cp.has_option("device", "tokens"):
        tokens = [t.strip() for t in cp["device"]["tokens"].split() if t.strip()]
    if not tokens:
        sys.exit("no device tokens: register from the app, or set [device] tokens")
    return apns, tokens


def post(host, auth, topic, token, body_json, push_type="alert", priority="10"):
    proc = subprocess.run([
        "curl", "--http2", "--silent", "--show-error",
        "--max-time", "15",
        "--write-out", "\n%{http_code}",
        "--header", f"authorization: bearer {auth}",
        "--header", f"apns-topic: {topic}",
        "--header", f"apns-push-type: {push_type}",
        "--header", f"apns-priority: {priority}",
        "--data", body_json,
        f"{host}/3/device/{token}",
    ], capture_output=True, text=True)
    out = proc.stdout.rsplit("\n", 1)
    return out[-1].strip(), out[0].strip()


def send(title, body, screen=None, silent=False):
    apns, tokens = load()
    configured = apns.get("environment", "development")
    host = HOSTS[configured]
    other = HOSTS["production" if configured == "development" else "development"]
    auth = make_jwt(apns)

    if silent:
        # content-available with no alert, sound or badge wakes the app for
        # about 30 seconds without showing the user anything. The app then
        # checks its own RSS feed and its own Transmission server and posts
        # local notifications itself, so nothing here needs to know either.
        # Apple requires push type "background" at priority 5; an alert-type
        # or priority-10 background push is rejected or silently dropped.
        payload = {"aps": {"content-available": 1}}
        push_type, priority = "background", "5"
    else:
        payload = {"aps": {"alert": {"title": title, "body": body},
                           "sound": "default", "thread-id": "transmote"}}
        push_type, priority = "alert", "10"
    if screen:
        payload["screen"] = screen
    body_json = json.dumps(payload, separators=(",", ":"))

    failures = 0
    for token in tokens:
        code, detail = post(host, auth, apns["topic"], token, body_json,
                            push_type, priority)

        # A token is only valid in the environment that issued it: a build
        # installed from Xcode gets a development token, one from TestFlight or
        # the App Store gets a production token, and the wrong host answers
        # 400 BadDeviceToken. Devices on both sides is the normal state while
        # testing, so fall back to the other host rather than giving up - there
        # is no way to tell the two kinds of token apart by looking at them.
        if code == "400" and "BadDeviceToken" in detail:
            code, detail = post(other, auth, apns["topic"], token, body_json,
                                push_type, priority)
            if code == "200":
                print(f"{'woke' if silent else 'sent to'} {token[:12]}... "
                      f"(on the other environment)")
                continue

        if code == "200":
            print(f"{'woke' if silent else 'sent to'} {token[:12]}...")
        else:
            failures += 1
            # 403 bad key/team, 410 token no longer registered
            print(f"FAILED {token[:12]}... HTTP {code} {detail}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--title")
    ap.add_argument("--body")
    ap.add_argument("--screen", choices=["rss", "main"])
    ap.add_argument("--silent", action="store_true",
                    help="wake the app to check for itself, showing nothing; "
                         "run this on a timer instead of polling the feed here")
    a = ap.parse_args()
    if not a.silent and not (a.title and a.body):
        ap.error("--title and --body are required unless --silent")
    sys.exit(send(a.title, a.body, a.screen, a.silent))
