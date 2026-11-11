#!/usr/bin/env python3
"""
Set a string value in an Info.plist by key, editing the text in place.

PlistBuddy and plutil rewrite the whole file and silently drop XML comments,
and this plist documents why several of its keys are there. Replace just the
<string> that follows the <key>, and leave every other byte alone.

    setplist.py Info.plist CFBundleVersion 8
"""
import io
import re
import sys

if len(sys.argv) != 4:
    sys.exit("usage: setplist.py <plist> <key> <value>")
path, key, value = sys.argv[1], sys.argv[2], sys.argv[3]

s = io.open(path, encoding="utf-8").read()
pattern = re.compile(
    r"(<key>" + re.escape(key) + r"</key>\s*<string>)([^<]*)(</string>)")
matches = pattern.findall(s)
if len(matches) != 1:
    sys.exit(f"{path}: expected one <key>{key}</key> with a <string>, found {len(matches)}")

s, n = pattern.subn(lambda m: m.group(1) + value + m.group(3), s)
io.open(path, "w", encoding="utf-8").write(s)
print(f"{key}: {matches[0][1]} -> {value}")
