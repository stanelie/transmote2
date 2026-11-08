# Transmote push server

Sends iOS notifications when a torrent finishes or the RSS feed gains an
item. iOS grants no reliable periodic background execution, so the push has
to originate here rather than from the phone.

| file | role |
|---|---|
| `transmote_push.py` | Sends an APNs push. ES256 JWT via `openssl`, HTTP/2 via `curl`. No pip dependencies. |
| `torrent-done.sh` | Transmission's `script-torrent-done` hook. Event-driven, no polling. |
| `rss_poll.py` | Cron job. Reads the feed URL from `devices.json`, not from config. |
| `register_server.py` | Receives `{token, rssFeedUrl}` from the app, writes `devices.json`. |

## Why registration exists

The RSS feed URL belongs to the app - the user can change it in Settings.
If this server kept its own copy it would silently poll a stale URL after
any change, with no error anywhere. So the app tells the server, every time
settings are saved.

## Environment

`environment = development` in `transmote_push.conf` matches a Debug build
installed from Xcode. A token minted in one environment is rejected by the
other; that mismatch is the usual cause of `BadDeviceToken`. Switch to
`production` only for a TestFlight or App Store build.

The APNs key must not be environment-restricted. A Production-only key
against the sandbox host returns `403 BadEnvironmentKeyInToken`.

## Files never to commit

`transmote_push.conf`, `devices.json`, `rss_seen.txt`, `.apns_jwt` and the
`.p8` key. All are gitignored; the repository is public.
