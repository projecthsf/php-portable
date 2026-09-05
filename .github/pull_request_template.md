## What this changes

<!-- What problem does this solve, and what is the root cause? -->

## How it was verified

<!-- What did you actually run/see? "Verified in the sandbox IDE: X went from A to B" is
     worth more than "should work". -->

## Checklist

- [ ] One logical change (see [CONTRIBUTING.md](../CONTRIBUTING.md))
- [ ] No changes to `version` in `build.gradle.kts` or `<change-notes>` in `plugin.xml` — maintainers cut releases
- [ ] `./gradlew buildPlugin verifyPlugin` passes locally
- [ ] No writes to shared locations outside the plugin's own directories (or explained above)
- [ ] Cross-platform: no shelled-out Unix tools, no hardcoded `~/.config` / `~/.cache`
- [ ] No long-running work on startup or the EDT
- [ ] Behaviour that isn't right for every project is exposed as a setting, defaulting to the safe option
