# Vibrefy

A lightweight, self-hosted media server.

Vibrefy streams video from a local library, and from your own cloud storage, straight to
the browser — with the server proxying every byte so nothing but the video ever leaves it.

## Design philosophy

**No database.** Vibrefy deliberately has no database and no server-side process beyond the
webapp itself. Configuration lives in a single `vibrefy.json`, and per-user state (cloud
account tokens, playback progress) lives in a small encrypted file per user. That's it —
copy the folder, and the whole install moves with it. No schema migrations, no service to
keep running, no backup strategy beyond "copy the files."

The trade-off is explicit: there's no watch history and no data collected to build viewing
preferences or recommendations. Vibrefy only ever remembers *where you left off* in a file,
so it can resume it — nothing about what you watched, when, or how often is retained once
you finish something. If you want a recommendation engine, this isn't it. If you want a
media server you can fully understand, back up with `cp`, and hand to a friend without
explaining a database schema, that's the point.

**Landscape covers.** Most media servers inherit the vertical/portrait poster convention
from movie posters and DVD boxes. Vibrefy generates its thumbnails and covers in 16:9,
matching the video itself — either from a sidecar image next to the file, or extracted
directly from the video's own embedded cover art (the MP4 `covr` atom). The grid is meant
to look like a shelf of screens, not a shelf of posters.

## Features

- **Browse and stream** a local video library, organized by folder, with resumable playback
  tracked per file.
- **Cloud storage mounts** — attach your own cloud storage account and browse it exactly
  like a local library. The server proxies streaming in bounded chunks (so a multi-gigabyte
  file never sits fully buffered in memory) and re-acquires expired download links
  transparently. Currently compatible with Google Drive, OneDrive, pCloud, and FileLu —
  more providers may be added over time.
- **Series auto-play** — files named with a season/episode pattern (`s03e02`, `s4e9`, ...)
  automatically advance to the next episode in the same folder when one finishes, sorted
  numerically rather than alphabetically (so `s4e9` comes before `s4e10`).
- **Resume tracking** — playback position is saved periodically and surfaced on Home as a
  watch list; entries are pruned automatically once their source file no longer exists.
- **Subtitles** — extracted directly from the video container, no sidecar subtitle files
  required.
- **OpenID login**, with an admin bootstrap on first run — no accounts to manually
  provision.
- **Media formats** — only `.mp4` and `.m4v` files are recognized as playable media, local
  or cloud.

## Tech stack

| Layer | Technology |
|---|---|
| Language / JDK | Java 11 |
| Server | Jakarta Servlets (annotation-driven, no `web.xml`) + JSP, on Tomcat 10 / Jetty |
| Build | Eclipse WST Dynamic Web Project — no Maven/Gradle |
| Frontend | Vanilla JS, no framework |

## Running it

On first run, with no `vibrefy.json` present, Vibrefy walks you through a setup screen to
register OpenID credentials; the first account to complete that setup becomes the
administrator.

## License

MIT — see [LICENSE](LICENSE).
