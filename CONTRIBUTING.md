# Contributing

Outside contributions are welcome — the plugin gets better and more widely useful when
people who hit real PHP setups fix them. This file describes what makes a contribution
easy to review and merge.

## Getting set up

```bash
./gradlew runIde        # sandbox IDE with the plugin loaded
./gradlew buildPlugin   # what CI builds
./gradlew verifyPlugin  # JetBrains Plugin Verifier — the same gate CI and releases run
```

Build with **JDK 17**. Kotlin 1.9's compiler is unstable on newer JDKs, so CI pins 17 and
you should too. See the build notes in [README.md](README.md).

## Scope a pull request to one change

One logical change per PR. A PR that fixes an indexing bug, adds a settings page, changes
the file icon and bumps the version four times is difficult to review, difficult to revert,
and difficult to bisect when something regresses six months later. Split those up — smaller
PRs get merged faster.

## Releases are separate from merges

**Contributors should not touch `version` in `build.gradle.kts` or `<change-notes>` in
`plugin.xml`.** Maintainers cut releases by bumping the version and pushing a `v*` tag; your
PR does not need to (and should not) do that. See [.github/workflows/publish.yml](.github/workflows/publish.yml).

## Commit messages

Follow what's already in `git log`: explain the **root cause**, not just the symptom. The
existing history is unusually good about this — "why the old behaviour was wrong, what the
underlying mechanism is, what was verified" — and it is the main reason this codebase is
maintainable. Please keep that up.

The same goes for code comments: this codebase comments the *why* (especially where it works
around platform or Phpactor behaviour), not the *what*. Match the surrounding density.

## Things reviewers will look for

These come up often enough to state up front:

- **Stay inside the plugin's own directories.** Writing to, or deleting from, shared
  locations (`~/.config`, `~/.cache`, anything a user's other tools also read) affects
  software outside the IDE. Sometimes it's genuinely necessary — if so, say why in the PR,
  and make sure it's documented in the user-visible change notes.
- **Behaviour changes that affect everyone belong in Settings.** If a change is right for
  one kind of project but wrong for others — suppressing diagnostics, changing indexing
  aggressiveness — expose it as a setting and let the default be the safe, conservative
  option. Users opt into the specialised behaviour.
- **Keep it cross-platform.** No shelling out to Unix tools (`cp`, `rm`), no hardcoded
  `~/.config` / `~/.cache` — use `java.nio.file` and resolve platform cache/config
  directories properly. The plugin is called *portable*.
- **Don't block startup.** Long work (indexing, scanning, subprocesses) belongs on a
  background task with a progress indicator, never in a constructor or on the EDT. A
  multi-minute stall reads as a frozen IDE.
- **Don't swallow failures silently.** `runCatching { }.getOrNull()` around an entire
  feature makes it impossible to tell "worked" from "silently did nothing". Log the failure.
- **Keep third-party assets attributed.** Vendored icons and code need their license header
  and a note on where they came from.

## CI

Every pull request runs [ci.yml](.github/workflows/ci.yml): `buildPlugin` then
`verifyPlugin`. Both must pass. CI on fork PRs reads no secrets, so it's safe to run on
outside contributions — but it also means nothing in a PR can publish anything.
