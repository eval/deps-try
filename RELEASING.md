# Releasing deps-try

## TL;DR

1. Ensure `CHANGELOG.md` has an entry with today's date.
2. GitHub → Actions → **Release** → *Run workflow* → provide a version:
   - **stable**: `vX.Y.Z` (e.g. `v0.13.0`)
   - **pre-release**: `vX.Y.Z-pre.N` (e.g. `v0.13.0-pre.4`)
   Anything else is rejected. The workflow creates the git tag for you (at
   the dispatched commit) — no need to tag manually.
   NB only cut a (pre-)release when there are new commits: dispatching twice
   from the same commit stacks multiple version-tags on it, making
   `git describe` (the source of unstable version strings) pick an arbitrary
   one.
3. Afterwards: spot-check the install channels (see below).

Every push to master *additionally* triggers the same workflow in
"unstable" mode (no version input; version derived via `git describe`,
e.g. `v0.13.0-4-g8b1908e`).

## What a run produces

| flow | GitHub releases updated/created | docker tags pushed (ghcr.io/eval/deps-try) |
|---|---|---|
| stable `vX.Y.Z` | tagged release `vX.Y.Z` + rolling `stable` + rolling `unstable`* | `latest`, `stable`, `X.Y.Z` |
| pre-release `vX.Y.Z-pre.N` | tagged release `vX.Y.Z-pre.N` (marked prerelease) + rolling `unstable` | `unstable` |
| master push | rolling `unstable` only | `unstable` |

*) the rolling `unstable` release is refreshed on *every* run (`if: always()`).

Each release carries `deps-try-bb.jar` (the uberjar), `deps-try.zip`
(source) and `VERSION`. The docker image downloads the jar from the rolling
`stable` release for stable runs, from rolling `unstable` otherwise
(multi-arch: amd64 + arm64).

Consumers per channel:
- **brew** and **bbin/manual**: rolling `stable` release (pre-releases and
  unstable never affect them)
- **docker**: `latest`/`stable` vs `unstable` image tags
- pre-release testing: install the tagged jar directly, e.g.
  `bbin install https://github.com/eval/deps-try/releases/download/vX.Y.Z-pre.N/deps-try-bb.jar --as deps-try-pre`

## Who sees an update-notification

Since v0.13.0 the boot message hints at newer versions:
`🩴 Version: v0.13.0 (v0.14.0 available — .../releases)`.

The source of truth is GitHub's `releases/latest`, i.e. **the newest stable
versioned release** — prereleases and drafts never count (and the rolling
`stable`/`unstable` releases don't either: `releases/latest` sorts by the
release's created-at, and those two are ancient objects that only get
updated). Consequently:

| running          | new pre-release appears | new stable appears |
|------------------|-------------------------|--------------------|
| stable `vX.Y.Z`  | no hint (by design)     | hint               |
| pre `vX.Y.Z-pre.N` | **no hint, ever**     | hint (incl. its own base going stable: `v0.13.0-pre.1` hints `v0.13.0`) |
| unstable `vX.Y.Z-N-gSHA` | no hint         | hint only when *genuinely* behind: a build 4 commits past `v0.13.0` is newer than `v0.13.0` (git-describe suffixes count as post-release), but older than `v0.13.1` |
| dev checkout     | never (tracks git, not releases) |

Mechanics (see `eval.deps-try.update-check`):
- The check runs **in the background of a REPL session** and caches to
  `<xdg-data-home>/deps-try/latest-version`; **booting only reads the cache
  and never touches the network**. So a new release shows up from the
  *second* boot after it: one session to learn, the next to announce.
- The hint repeats every boot until upgraded; upgrading is left to the
  user's install channel.
- Offline/API-failures are silent; the last known value is kept.
- Opt-out: set env `DEPS_TRY_NO_UPDATE_CHECK`.
- Installs older than v0.13.0 predate the feature and never notify.

## Post-release spot-checks

- `brew update && brew upgrade deps-try` / re-run the bbin install /
  `docker run -it --pull always ghcr.io/eval/deps-try` — `deps-try -v`
  reports the new version.
- docker: boot message shows emoji (guards the image's UTF-8 locale).
- On a previous install: run one session, restart — the update hint shows.
