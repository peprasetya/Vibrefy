# Vibrefy client API

For building a native client — Android, Linux, anything that isn't the web UI.

The reason this exists: browsers on Android and Linux have patchy AC3 / E-AC3 support, so a
library that plays fine in Safari is silent elsewhere. Vibrefy **never transcodes** — it
serves raw MP4 byte ranges — so the fix is a client with a player that handles those codecs
(ExoPlayer, mpv, VLC, libVLC). This document is how such a client talks to the server.

> **Status:** the JSON endpoints landed in `6c2c77a` and the browser-link login after it.
> Neither has yet been exercised against a running server. Treat response shapes as
> intended-and-implemented, not battle-tested.

---

## The one rule

Send **`Accept: application/json`** on every request. That is the entire opt-in.

The same URLs serve the web UI. The server decides what to answer with:

| Request | Answer |
|---|---|
| `Accept: application/json` | JSON |
| `?ajaxcall=1` | the web UI's XML fragments |
| anything else (a browser) | the HTML app shell |

Do **not** send `ajaxcall=1` — it wins over the Accept header and you will get XML.

`GET` and `POST` both work everywhere; parameters may be query string or form-encoded body.

---

## Sessions and cookies

Authentication is a plain `JSESSIONID` cookie. **Your client needs a cookie jar, and must
send it on every request — including stream fetches.**

That last part is not optional politeness. The session is a **4-hour idle** timeout that
only refreshes on requests carrying the cookie. The `/stream/<sessionId>/…` form resolves
its session by looking the id up in a table, which does *not* count as activity. A client
that streams a three-hour film using only the URL form, with no cookie, can have its session
expire mid-playback.

Signing in **rotates the session id** (`changeSessionId`, same as the web login). Follow the
`Set-Cookie`, and re-read the `session` value from `/files` afterwards — any URL you built
with the old id is dead.

---

## Logging in

Vibrefy signs users in with OpenID (Google/Microsoft/Yahoo) in a browser popup. A native
client never hosts that popup itself. Instead it either hands the sign-in to a real browser
and waits for approval, or carries a login across from a device that already has one.

Every pairing credential is **single use** and throttled to 5 failed attempts per 5 minutes
per source address. Typed codes are 8 digits and live **3 minutes**; browser-link keys are
client-generated and live **10 minutes**.

There are three ways in. All of them end the same way: *your* session gets signed in and
its cookie rotated.

| | Best for | The user does |
|---|---|---|
| **A — browser link** | a desktop app that can open a browser | signs in with Google, clicks Approve |
| **B — this device shows a code** | a TV box, a console | types the code on a phone, clicks Approve |
| **C — this device enters a code** | a headset, a desktop app | reads a code off a signed-in device |

### Direction A — open a browser at a link

The one to use on desktop. Nothing is typed: your app generates a key, registers it, and
opens the user's browser. They sign in with Google there and land straight on an Approve
screen — the same Google sign-in they already use on the web, with no popup for you to host.

**1. Generate a key.** Your app, not the server:

- **32 to 96 characters**, from `A-Z a-z 0-9 - _` (URL-safe base64). Anything else is refused.
- From a **CSPRNG**. 32 bytes of `/dev/urandom`, base64url-encoded, is the intended shape:
  the key is the whole secret, so entropy is on you. Never derive it from a machine id, a
  hostname, a username, or a timestamp.
- **A fresh key per sign-in attempt.** It is single-use; reusing one is refused as a collision.

**2. Register it**, with your cookie jar, before opening the browser:

```
POST /pair            Accept: application/json
                      order=Link&key=<key>
→ {"state":"waiting","code":"<key>","url":"https://media.example.com/pair/<key>",
   "seconds":600,"account":null}
```

Registration binds the key to *your* session. That binding is what makes the key safe to
put in a URL: even someone who reads the key off the screen cannot collect the approval,
because only the session that registered it is allowed to poll for it.

`{"state":"invalid"}` with a `message` means the key was malformed or already in use —
generate a new one.

**3. Open `url` in the user's browser.** Use the `url` from the response rather than
building it yourself; it is resolved through the proxy headers, so it is correct even when
Vibrefy sits behind nginx or Cloudflare.

```
xdg-open  <url>      # Linux
open      <url>      # macOS
start     <url>      # Windows
```

Show the URL on screen as well, so the user can copy it to a phone if the desktop has no
browser.

**4. Poll**, exactly as in direction B:

```
POST /pair            order=Poll&code=<key>
→ {"state":"waiting","seconds":540}
→ {"state":"done","account":"you@example.com"}     ← signed in, cookie rotated
→ {"state":"expired"}                              ← past 10 minutes, new key, start over
```

Link keys live **10 minutes**, not the 3 minutes a typed code gets — a Google sign-in with
an account chooser and a second factor does not reliably fit in three.

#### The URL format

```
https://<your-vibrefy-host>/pair/<key>
```

That is the whole link. No query string, and there must not be one.

**The key goes in the path, and it has to.** When a signed-out browser opens that URL,
Vibrefy serves the app shell, which sends the user to the Google sign-in. Once that
finishes, the app resumes by re-requesting `window.location.pathname` — the *path*, with
the query string discarded. A key in `?key=…` is silently dropped at exactly that moment
and the user lands on an empty Devices page. In the path it survives, and the browser opens
directly on the Approve screen.

The charset requirement above exists for the same reason: the key sits in a path segment
with nothing to percent-encode, so there is nothing for either side to get wrong.

**What the user sees.** Signed out: the normal Vibrefy sign-in, then the Approve screen.
Already signed in: the Approve screen immediately. Either way it shows the device name,
address and user-agent your registering request arrived with, and a single Approve button.
There is deliberately no auto-approve — a link that signed a device in just by being opened
would be a phishing primitive.

### Direction B — this device shows a code

For a device with a screen but awkward text entry (TV box, console).

```
POST /pair            Accept: application/json
                      order=Request
→ {"state":"waiting","code":"12345678","seconds":180,"account":null}
```

Show `code` to the user. They open Vibrefy on any signed-in device → **📱 Devices** → enter
the code → they are shown your device's name, IP and user-agent → **Approve**.

Meanwhile poll every ~3 seconds, **with the same cookie jar** — the code is bound to the
session that requested it, so a different session cannot redeem it:

```
POST /pair            order=Poll&code=12345678
→ {"state":"waiting","code":"12345678","seconds":142}
→ {"state":"done","account":"you@example.com"}      ← signed in, cookie rotated
→ {"state":"expired"}                               ← past 3 minutes, start over
```

### Direction C — this device enters a code

For a device that can type but is awkward to approve *for* (a headset, a desktop app).

The user opens Vibrefy on a signed-in device → **📱 Devices** → **Show a code**. You submit it:

```
POST /pair            order=Claim&code=87654321
→ {"state":"done","account":"you@example.com"}      ← signed in, cookie rotated
→ {"state":"invalid","message":"That code is not valid, or it has expired."}
```

Unlike direction A this code is a bearer credential for its 3-minute life — anyone who reads
it off the screen can use it. That is why it is short-lived, single-use, and only minted on
an explicit request.

### `state` values

`waiting` · `done` · `expired` · `invalid` · `offered` (you called `order=Offer`) · `idle`

### Orders

| `order` | Who sends it | Effect |
|---|---|---|
| `Link` | your app | registers a client-generated key, returns the browser `url` |
| `Request` | your app | mints an 8-digit code to display |
| `Poll` | your app | checks whether `code` has been approved; signs you in when it has |
| `Claim` | your app | redeems a code a signed-in device is showing |
| `Find` / `Approve` / `Offer` | the web UI | the approver's side; a client has no reason to send these |

---

## Browsing

One endpoint. The root listing **is** the library list, so there is no separate call for it.

```
GET /files                          → libraries and cloud mounts
GET /files/<library>/<sub>/<dir>    → folders and playable files
```

```json
{
  "session": "1A2B3C…",
  "library": "MyLib",
  "path": "MyLib/Show",
  "crumbs": ["MyLib", "Show"],
  "admin": false,
  "cloud": false,
  "items": [
    {"name":"Ep 1.mp4","path":"MyLib/Show/Ep 1.mp4","type":3,"time":124,"modified":1700000000000}
  ]
}
```

| Field | Meaning |
|---|---|
| `session` | your current session id — build stream URLs from this, not from a cached copy |
| `library` | `null` at the root |
| `path` | full path of the folder you are in, `""` at the root |
| `crumbs` | path segments, for a breadcrumb |
| `admin` | whether this account is an administrator |
| `cloud` | whether this folder lives on a cloud mount |
| `error` | present only when something went wrong |

### Item `type`

| Value | Meaning |
|---|---|
| `1` | library / volume — descend into it |
| `2` | folder |
| `3` | playable media (`.mp4` / `.m4v`) |
| `4` | link |
| `5` | cloud mount — descend into it |

`time` is the resume position **in seconds** (0 = unwatched). `modified` is the file mtime in
epoch milliseconds, `0` when the source could not report one.

Only `.mp4` and `.m4v` are recognised as media, local or cloud.

> There is no server-side "recently added" or "random" list. Both are trivially derived from
> `items` — sort by `modified` descending, or shuffle — so sort what you already hold rather
> than asking for it twice.

---

## Home / continue watching

```
GET /home
→ {"watching":[ {…item…}, … ]}
```

Same item shape, already sorted most-recently-watched first, and spanning every library.
Entries whose file or cloud mount has disappeared are pruned as a side effect of this call.

There is no `new` or `random` list — with no database, producing them would mean walking
every library on each load.

---

## Playing

These endpoints predate the JSON work and need **no `Accept` header**. Note the session id
sits in the *path*.

| URL | Returns |
|---|---|
| `/stream/<session>/<library>/<path>` | `video/mp4`, honours `Range`, `Accept-Ranges: bytes` |
| `/subtitles/<session>/<library>/<path>` | JSON track list |
| `/subtitle/<session>/<library>/<path>?track=N` | `text/vtt` for one track |
| `/medialist/<session>/<library>/<path>` | JSON array of sibling media filenames |
| `/thumbmp4/<library>/<path>` | `image/jpeg` — 320px wide, **cookie auth, no session segment** |
| `/covermp4/<library>/<path>` | `image/jpeg` — full size, **cookie auth, no session segment** |

Subtitle tracks come out of the MP4 container itself:

```json
[{"index":0,"label":"English","lang":"eng"},{"index":1,"label":"English - SDH","lang":"eng"}]
```

Feed the file straight to your player — the server does not transcode, which is the whole
point. Covers are 16:9, taken from a sidecar image next to the file or from the MP4 `covr`
atom.

`/medialist` gives you the other media files in the same folder, which is how the web UI
finds the next episode.

---

## Saving progress

```
POST /progress        media=files/MyLib/Show/Ep 1.mp4&time=124
→ {"ok":true}
```

- `media` is the **browse** path prefixed with `files/`. It must match exactly or resume
  silently fails to line up with the web UI.
- `time` is seconds.
- **`time` below `-10` deletes the entry.** The web client sends `-99` when a file finishes.
- `{"ok":false}` means the write was rejected — no usable session, or no `media`.

The web UI saves every 10 seconds during playback (and only when the position moved by 5+
seconds), on pause, and on end. Matching that keeps both clients consistent, and — because
this call carries your cookie — it is also what keeps the 4-hour idle timer alive.

---

## Paths and encoding

**Percent-encode each path segment; leave the `/` separators alone.** Filenames genuinely
contain characters that are reserved in a URL, and getting this wrong fails silently.

```
/files/MyLib/Show/Ep%201%3F%20%26%20%3Codd%3E.mp4     ✅
/files/MyLib%2FShow%2FEp 1? & <odd>.mp4               ❌
```

Three specific traps:

1. **`?` truncates the path.** A bare `?` in a filename starts the query string; the server
   sees a shortened path, finds nothing, and returns an empty success. Encode it as `%3F`.
2. **Never send a bare `+`.** The server decodes paths with a form decoder, which reads `+`
   as a space. A literal `+` in a filename must be `%2B`. Spaces must be `%20`, not `+`.
3. **Don't encode the separators.** `%2F` is rejected outright by Jetty as an ambiguous path
   separator, and a doubled `//` is rejected the same way.

`encodeURIComponent` per segment, joined with `/`, does the right thing in all three cases.

Unicode normalization is handled server-side — a name stored NFD (macOS) still resolves when
requested NFC, and vice versa — so you do not need to normalize before sending.

---

## Errors

| Status | Meaning |
|---|---|
| `200` + JSON | fine |
| `401` `{"error":"unauthenticated"}` | no session, or it expired — pair again |
| `200` + `{"error":"…"}` | the request was understood but the path was not |

**The trap worth knowing:** `/stream`, `/subtitle`, `/subtitles` and `/medialist` are open
endpoints that resolve their session from the URL, so they **never return 401**. An unknown
or expired session id gets a **`200` with an empty body** (or `[]`). Treat an empty stream
response as "the session died", not "the file is missing" — otherwise an expired session
looks exactly like a corrupt library.

---

## A first client, end to end

1. Generate a key, `POST /pair` `order=Link&key=…`, open the returned `url` in a browser.
   (On a device with no browser, `order=Request` and show the 8-digit code instead.)
2. Poll `order=Poll&code=…` every 3s until `"state":"done"`. Save the rotated cookie.
3. `GET /files` → libraries. Stash `session`.
4. `GET /files/<library>/…` → browse. Fetch `/thumbmp4/<path>` for tiles.
5. Play `/stream/<session>/<library>/<path>`, seek to `time` from the item.
6. `GET /subtitles/<session>/…` → attach tracks via `/subtitle/…?track=N`.
7. `POST /progress` every 10s, on pause, and `-99` on finish.
8. On `401`, or an empty stream body, go back to step 1.

Send the cookie on all of it.
