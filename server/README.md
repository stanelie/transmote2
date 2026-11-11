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

## Waking the app instead of polling for it

`rss_poll.py` fetches the feed here and pushes a ready-made notification. That
means this machine has to hold a copy of the feed URL — a setting that belongs
to the app, since the user can change it — plus `rss_seen.txt` to remember what
it already announced.

`--silent` avoids all of that. It sends a push with no content at all:

    transmote_push.py --silent

The app wakes for about 30 seconds, checks its *own* RSS feed and its *own*
Transmission server, and posts local notifications itself. This machine learns
nothing and stores nothing. A timer is all it is:

    # every six hours, tell Transmote to go and look
    0 */6 * * * /opt/transmote/transmote_push.py --silent >/dev/null 2>&1

Worth knowing before relying on it:

- **A force-quit app gets no silent pushes.** iOS withholds them until the user
  opens the app again. Visible alert pushes, like the completion hook's, still
  arrive. This is the same limitation `BGAppRefreshTask` has, so the wake-up is
  an improvement on it, not a cure.
- **Low Power Mode holds them**, and iOS throttles them generally. Six-hourly is
  well inside any budget, but no single wake is guaranteed.
- **Nothing here can tell whether the check found anything.** The app reports
  that to iOS, not to us, which is what earns it future wake-ups.

Because a wake-up carries no content, the sender does not need to be the user's
Transmission box, or know anything about them. One timer anywhere can serve
every device whose token it holds.
