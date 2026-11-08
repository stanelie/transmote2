#!/bin/sh
# Transmission runs this the moment a torrent finishes.
#
# Enable in Transmission's settings.json (stop the daemon first, it rewrites
# the file on exit):
#     "script-torrent-done-enabled": true,
#     "script-torrent-done-filename": "/path/to/torrent-done.sh"
#
# Transmission provides TR_TORRENT_NAME, TR_TORRENT_DIR, TR_TORRENT_ID,
# TR_TIME_LOCALTIME and friends in the environment.

DIR="$(dirname "$(readlink -f "$0")")"
exec "$DIR/transmote_push.py" \
    --title "Download complete" \
    --body "${TR_TORRENT_NAME:-a torrent}" \
    --screen main
