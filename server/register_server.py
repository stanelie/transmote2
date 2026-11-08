#!/usr/bin/env python3
"""
Tiny registration endpoint for the Transmote app.

The app POSTs its APNs token AND its current RSS feed URL here, because the
feed URL is owned by the app - the user can change it in Settings - so the
server cannot keep its own copy without going stale.

    POST <SECRET_PATH>/register
    {"token": "...", "rssFeedUrl": "https://...", "platform": "ios"}

State lands in devices.json, which transmote_push.py and rss_poll.py read.

Run behind your existing TLS terminator, e.g.:
    TRANSMOTE_SECRET=some-long-random-string ./register_server.py 8099
and proxy https://bt.stanelie.com/<same-secret>/ to it. The secret lives in
the URL path - a capability URL - so the app needs only one setting and no
separate credential.
"""

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
DEVICES = os.path.join(HERE, "devices.json")
SECRET = os.environ.get("TRANSMOTE_SECRET", "")
MAX_BODY = 8192


def load():
    if os.path.exists(DEVICES):
        with open(DEVICES) as fh:
            return json.load(fh)
    return {}


def save(data):
    tmp = DEVICES + ".tmp"
    with open(tmp, "w") as fh:
        json.dump(data, fh, indent=2, sort_keys=True)
    os.replace(tmp, DEVICES)          # atomic; readers never see a half file


class Handler(BaseHTTPRequestHandler):
    server_version = "transmote-register/1"

    def _reply(self, code, text="")  :
        body = text.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        path = self.path.rstrip("/")
        if SECRET and not path.startswith("/" + SECRET):
            return self._reply(404, "not found")
        if not path.endswith("/register"):
            return self._reply(404, "not found")

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self._reply(400, "bad length")
        if length <= 0 or length > MAX_BODY:
            return self._reply(400, "bad length")

        try:
            payload = json.loads(self.rfile.read(length))
            token = str(payload["token"]).strip()
            feed = str(payload.get("rssFeedUrl", "")).strip()
        except Exception:
            return self._reply(400, "bad json")

        if not token or len(token) > 200 or any(c not in "0123456789abcdefABCDEF" for c in token):
            return self._reply(400, "bad token")

        devices = load()
        devices[token] = {
            "token": token,
            "rssFeedUrl": feed,
            "platform": payload.get("platform", "ios"),
        }
        save(devices)
        print(f"registered {token[:12]}... feed={feed or '(none)'}", flush=True)
        self._reply(200, "ok")

    def log_message(self, *args):
        pass          # keep the journal quiet; we print what matters above


if __name__ == "__main__":
    if not SECRET:
        print("warning: TRANSMOTE_SECRET unset - the endpoint is unauthenticated",
              file=sys.stderr)
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    # Default to loopback (for use behind a reverse proxy). Set
    # TRANSMOTE_BIND=0.0.0.0 to serve a private overlay such as ZeroTier
    # directly, where the secret path is the only guard.
    bind = os.environ.get("TRANSMOTE_BIND", "127.0.0.1")
    print(f"listening on {bind}:{port}", flush=True)
    HTTPServer((bind, port), Handler).serve_forever()
