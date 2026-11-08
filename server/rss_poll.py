#!/usr/bin/env python3
"""
Poll the RSS feed and push once per genuinely new item.

The feed URL is read from devices.json, which the app registers - the user
can change the feed in Settings, so the server must not hold its own copy.
The feed itself is a public URL, so this needs no access to the RSS server,
only an ordinary HTTP GET from yours. Run from cron, e.g. every 15 minutes:

    */15 * * * * /path/to/rss_poll.py >> /var/log/transmote-rss.log 2>&1

Seen GUIDs are kept in rss_seen.txt beside the script. The first run marks
everything as seen WITHOUT notifying, so you do not get a burst of pushes
for the existing backlog.
"""

import json
import os
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SEEN = os.path.join(HERE, "rss_seen.txt")
DEVICES = os.path.join(HERE, "devices.json")
PUSH = os.path.join(HERE, "transmote_push.py")
MAX_PUSHES = 5          # never spam; summarise beyond this


def load_seen():
    if not os.path.exists(SEEN):
        return None                      # signals "first run"
    with open(SEEN) as fh:
        return {line.strip() for line in fh if line.strip()}


def save_seen(guids):
    with open(SEEN, "w") as fh:
        fh.write("\n".join(sorted(guids)))


def feed_urls():
    """Feed URLs come from the app via register_server.py. The user can change
    the feed in Settings, so hardcoding it here would silently go stale."""
    if not os.path.exists(DEVICES):
        return []
    with open(DEVICES) as fh:
        devices = json.load(fh)
    return sorted({d["rssFeedUrl"] for d in devices.values() if d.get("rssFeedUrl")})


def fetch_items(FEED_URL):
    req = urllib.request.Request(FEED_URL, headers={"User-Agent": "transmote-rss-poll/1"})
    with urllib.request.urlopen(req, timeout=30) as r:
        root = ET.fromstring(r.read())
    items = []
    for item in root.iter("item"):
        title = (item.findtext("title") or "").strip()
        link = (item.findtext("link") or "").strip()
        guid = (item.findtext("guid") or link).strip()
        if title and guid:
            items.append((guid, title))
    return items


def push(title, body):
    subprocess.run([PUSH, "--title", title, "--body", body, "--screen", "rss"], check=False)


def main():
    urls = feed_urls()
    if not urls:
        print("no feed registered yet - open the app with a Push server URL set")
        return 0

    items = []
    for url in urls:
        try:
            items.extend(fetch_items(url))
        except Exception as e:
            print(f"failed to read {url}: {e}", file=sys.stderr)
    if not items:
        print("feed returned no items")
        return 0

    seen = load_seen()
    all_guids = {g for g, _ in items}

    if seen is None:
        save_seen(all_guids)
        print(f"first run: marked {len(all_guids)} existing items as seen, no pushes sent")
        return 0

    new = [(g, t) for g, t in items if g not in seen]
    if not new:
        print("nothing new")
        return 0

    if len(new) <= MAX_PUSHES:
        for _, title in new:
            push("New in RSS", title)
    else:
        push("New in RSS", f"{len(new)} new items")

    save_seen(seen | all_guids)
    print(f"notified about {len(new)} new item(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
