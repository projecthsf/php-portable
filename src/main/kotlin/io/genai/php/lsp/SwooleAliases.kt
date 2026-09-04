package io.genai.php.lsp

import java.nio.file.Files
import java.nio.file.Path

/**
 * IDE-visible declarations for the Swoole extension's legacy runtime aliases
 * (`swoole_table`, `swoole_server`, `Co\*`, …).
 *
 * Those alias classes only exist at runtime — they are registered with
 * `class_alias()` calls inside the bundled phpstorm-stubs (`swoole/aliases.php`), which no
 * static analyzer resolves. The result: every use of a legacy alias in a swoole_system-style
 * project is flagged `Class "swoole_table" not found`, and neither navigation nor member
 * completion works on them.
 *
 * This generates real `class X extends \Source {}` declarations (one file per namespace —
 * WorseReflection does not reflect classes in files using bracketed multi-namespace syntax)
 * into the extracted stubs directory, so the aliases resolve like any other stub class:
 * go-to-definition lands on the generated file, members/constants are inherited from the
 * canonical `Swoole\*` stubs. [knownAliases] exposes the generated set so the client-side
 * diagnostic filter (see PhpClientFeatures) can drop the remaining false "not found"
 * findings for exactly these names — Phpactor's diagnostics pipeline does not consult the
 * stub locator for them, so the shim alone cannot silence it.
 */
object SwooleAliases {

    private val ALIAS_LINE = Regex("""class_alias\((.+?)::class,\s*(.+?)::class\);""")

    /** Generated marker (content = number of aliases) — regenerate when it changes. */
    private fun markerPath(stubsDir: Path): Path = stubsDir.resolve(".swoole-aliases.generated")

    /** Flat list of generated alias FQNs, one per line (read by the diagnostic filter). */
    private fun namesPath(stubsDir: Path): Path = stubsDir.resolve(".swoole-aliases.names")

    @Volatile
    private var cachedAliases: Set<String>? = null

    /**
     * Generate the alias declarations into [stubsDir] if needed. Idempotent: skips when the
     * marker matches the current `swoole/aliases.php`. When regenerating, the WorseReflection
     * stub-map caches under `~/.cache/phpactor/worse-reflection` (the `.map` files) are
     * deleted so they get rebuilt with the new files. Blocking but fast (a few small
     * writes); call off the EDT.
     */
    fun ensureGenerated(stubsDir: Path) {
        runCatching {
            val source = stubsDir.resolve("swoole/aliases.php")
            if (!Files.isRegularFile(source)) return

            val pairs = ALIAS_LINE.findAll(Files.readString(source))
                .map { it.groupValues[1].trim() to it.groupValues[2].trim() }
                .toList()
            if (pairs.isEmpty()) return

            val marker = markerPath(stubsDir)
            val markerText = pairs.size.toString()
            if (Files.isRegularFile(marker) && Files.readString(marker) == markerText) return

            // group by target namespace: "" (global) or e.g. "Co"
            val byNamespace = pairs.groupBy({ (_, target) ->
                if (target.contains('\\')) target.substringBeforeLast('\\') else ""
            }, { (source0, target) ->
                val name = target.substringAfterLast('\\')
                name to source0
            })

            val names = sortedSetOf<String>()
            for ((ns, entries) in byNamespace) {
                val file = if (ns.isEmpty()) stubsDir.resolve("swoole_aliases.php")
                else stubsDir.resolve(ns.replace('\\', '_') + "_aliases.php")
                val body = entries.joinToString("\n") { (name, src) ->
                    "class $name extends \\$src {}"
                }
                val content = if (ns.isEmpty()) {
                    "<?php\n/** Auto-generated Swoole legacy alias stubs (php-portable). */\n$body\n"
                } else {
                    "<?php\n\nnamespace $ns;\n\n/** Auto-generated Swoole legacy alias stubs (php-portable). */\n$body\n"
                }
                Files.writeString(file, content)
                names += entries.map { (name, _) -> if (ns.isEmpty()) name else "$ns\\$name" }
            }

            Files.writeString(namesPath(stubsDir), names.joinToString("\n"))
            Files.writeString(marker, markerText)
            cachedAliases = names

            // The WorseReflection stub maps cache the *previous* file set — drop them so the
            // next session rebuilds the maps including the generated alias files.
            val mapDir = Path.of(System.getProperty("user.home"), ".cache", "phpactor", "worse-reflection")
            if (Files.isDirectory(mapDir)) {
                Files.list(mapDir).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".map") }
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    /**
     * The generated alias FQNs (e.g. `swoole_table`, `Co\Channel`). Empty when generation has
     * not run or found no aliases — in that case callers should not filter anything.
     */
    fun knownAliases(): Set<String> {
        cachedAliases?.let { return it }
        val names = runCatching {
            val stubsDir = Path.of(System.getProperty("user.home"), ".php-portable", "phpstorm-stubs")
            val file = namesPath(stubsDir)
            if (Files.isRegularFile(file)) {
                Files.readAllLines(file).filter { it.isNotBlank() }.toSet()
            } else {
                emptySet()
            }
        }.getOrDefault(emptySet())
        cachedAliases = names
        return names
    }
}
