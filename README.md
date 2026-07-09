# GenAI PHP Portable — IntelliJ IDEA Community plugin

Minimal PHP support for **IntelliJ IDEA Community**, which does not bundle the paid
PhpStorm language plugin. Built the same way as `../jenkinsfile-plugin`
(IntelliJ Platform Gradle Plugin 2.x, Kotlin).

## What's in this MVP

1. **Portable PHP SDK + manager** (`sdk/`, `settings/`) — a `SdkType` + `SdkDownload`,
   the same mechanism the IDE uses for JDKs. Manage interpreters from a dedicated panel:
   **Settings ▸ Languages & Frameworks ▸ PHP Interpreters** — *Download PHP…*,
   *Add from Disk…*, *Remove* (optionally deleting files), *Open Folder*. Downloads land
   in `~/.php-portable`. (Also available via *Project Structure ▸ SDKs ▸ Download*.)
2. **Run PHP script** (`run/`) — a run configuration that executes a `.php` file with
   the selected PHP SDK (or an explicit interpreter path).
3. **Syntax highlighting** (`lang/`) — a lightweight, lexer-based highlighter for
   `.php` files (tags, keywords, strings, comments, `$variables`, numbers).

## Run it

```bash
cd php-portable
./gradlew runIde        # launches a sandbox IDEA Community with the plugin loaded
```

Then:
- Open `examples/hello.php` → see highlighting.
- *Project Structure ▸ SDKs ▸ + ▸ Download PHP* → download a portable PHP.
- *Run ▸ Edit Configurations ▸ + ▸ PHP Script* → pick the SDK + `hello.php`, Run.

## Build notes

- Verified to compile against **IntelliJ IDEA Community 2024.1** (`./gradlew compileKotlin`).
- **Build with a JDK ≤ 21.** Kotlin 1.9.x's compiler crashes on JDK 24+ (`CoreJrtFileSystem`).
  On this machine use the bundled JBR 17:
  ```bash
  JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home ./gradlew runIde
  ```
  (IntelliJ itself picks the right JBR automatically when you build from the IDE.)

## Things you'll likely want to change

- **`PhpDownloads` URLs are examples** — patch versions go stale. Point them at current
  builds: Windows → windows.php.net; macOS/Linux → static-php-cli (dl.static-php.dev).
- **`SdkDownload` lives in** `com.intellij.openapi.roots.ui.configuration.projectRoot`
  and its callback is `java.util.function.Consumer` (`.accept(task)`) — already wired.
- **`addBrowseFolderListener`** is deprecated in newer platforms but still works; the
  non-deprecated replacement is `withTextBrowseButton`/`TextBrowseFolderListener`.

## Next steps (not in this MVP)

- **Semantic features** (completion, go-to-def, inspections): wrap a PHP language server
  (Intelephense / phpactor) via **LSP4IJ** — the built-in IntelliJ LSP API is Ultimate-only
  and won't run in Community.
- **Xdebug** integration (DBGp) for debugging.
- Fetch the download catalog from a JSON manifest instead of hardcoding.

## Layout

```
src/main/kotlin/io/genai/php/
  lang/      PhpLanguage, PhpFileType, PhpTokenTypes, PhpLexer, PhpSyntaxHighlighter(+Factory)
  sdk/       PhpSdkType, PhpRelease, PhpDownloads, PhpSdkManager, PhpInterpreterActions,
             PhpSdkDownloadTask, PhpDownloadDialog
  run/       PhpRunConfigurationType/Factory/Options/Configuration, PhpSettingsEditor,
             PhpRunConfigurationProducer
  settings/  PhpInterpretersConfigurable, PhpInterpreterSettings
  statusbar/ PhpStatusBarWidget(+Factory)
  notify/    PhpSetupNotificationProvider
src/main/resources/META-INF/plugin.xml, icons/php.svg
examples/hello.php
```

## License

[Apache License 2.0](LICENSE) © 2026 GenAI
