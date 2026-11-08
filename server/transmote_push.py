#!/usr/bin/env python3
"""
Send an APNs push to the Transmote iOS app.

Talks to Apple directly over HTTP/2 with a JWT signed by a .p8 auth key, so
there is no third-party push service in the path and nothing to subscribe to.

Config lives in transmote_push.conf next to this script (INI):

    [apns]
    key_file  = /etc/transmote/AuthKey_XXXXXXXXXX.p8
    key_id    = XXXXXXXXXX          ; the Key ID shown when you made the key
    team_id   = TTT8628BZJ
    topic     = ca.exmachina.transmote2   ; the iOS bundle id
    ; development for a Debug build installed from Xcode,
    ; production once the app is distributed via TestFlight/App Store
    environment = development

    [device]
    tokens = <the token shown in Transmote's Settings screen>
             ; one per line for several devices

Usage:
    transmote_push.py --title "Download complete" --body "Some.Torrent.Name"
    transmote_push.py --title "New in RSS" --body "Episode name" --screen rss
"""

import argparse
import configparser
import json
import os
import sys
import time

try:
    import httpx  # HTTP/2 capable
except ImportError:
    sys.exit("needs httpx with http2 support:  pip3 install 'httpx[http2]' pyjwt cryptography")

try:
    import jwt  # PyJWT
except ImportError:
    sys.exit("needs PyJWT:  pip3 install pyjwt cryptography")

HERE = os.path.dirname(os.path.abspath(__file__))
CONF = os.path.join(HERE, "transmote_push.conf")

HOSTS = {
    "development": "https://api.sandbox.push.apple.com",
    "production": "https://api.push.apple.com",
}


def load_config():
    if not os.path.exists(CONF):
        sys.exit(f"missing config: {CONF}")
    cp = configparser.ConfigParser()
    cp.read(CONF)
    tokens = [t.strip() for t in cp["device"]["tokens"].split() if t.strip()]
    if not tokens:
        sys.exit("no device tokens configured")
    return cp["apns"], tokens


def make_jwt(apns):
    """APNs provider token. Valid up to an hour; Apple rejects a token
    refreshed more often than every 20 minutes, so cache it on disk."""
    cache = os.path.join(HERE, ".apns_jwt")
    if os.path.exists(cache) and time.time() - os.path.getmtime(cache) < 1800:
        with open(cache) as fh:
            return fh.read().strip()

    with open(apns["key_file"]) as fh:
        key = fh.read()
    token = jwt.encode(
        {"iss": apns["team_id"], "iat": int(time.time())},
        key,
        algorithm="ES256",
        headers={"kid": apns["key_id"]},
    )
    with open(cache, "w") as fh:
        fh.write(token)
    os.chmod(cache, 0o600)
    return token


def send(title, body, screen=None):
    apns, tokens = load_config()
    host = HOSTS[apns.get("environment", "development")]
    auth = make_jwt(apns)

    payload = {
        "aps": {
            "alert": {"title": title, "body": body},
            "sound": "default",
            # Let iOS group Transmote's notifications together.
            "thread-id": "transmote",
        }
    }
    if screen:
        payload["screen"] = screen

    failures = 0
    with httpx.Client(http2=True, timeout=10) as client:
        for token in tokens:
            r = client.post(
                f"{host}/3/device/{token}",
                headers={
                    "authorization": f"bearer {auth}",
                    "apns-topic": apns["topic"],
                    "apns-push-type": "alert",
                    "apns-priority": "10",
                },
                json=payload,
            )
            if r.status_code == 200:
                print(f"sent to {token[:12]}...")
            else:
                failures += 1
                # 400 BadDeviceToken / 403 bad key / 410 token no longer valid
                print(f"FAILED {token[:12]}... {r.status_code} {r.text}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--title", required=True)
    ap.add_argument("--body", required=True)
    ap.add_argument("--screen", choices=["rss", "main"], help="screen to open on tap")
    a = ap.parse_args()
    sys.exit(send(a.title, a.body, a.screen))
