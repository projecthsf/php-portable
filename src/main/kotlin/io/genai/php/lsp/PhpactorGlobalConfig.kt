package io.genai.php.lsp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Maintains Phpactor's GLOBAL config file (`~/.config/phpactor/phpactor.json`).
 *
 * Why a global file and not just LSP initializationOptions: Phpactor's diagnostics pipeline
 * runs in a BASE container that never sees initializationOptions — it reads config FILES.
 * Without this file, the base container uses defaults: a symlink-blind scanner, the default
 * (unshared) index path and no file-size headroom, which is exactly why non-Composer projects
 * (swoole_system-style) got flooded with false `Class "X" not found` diagnostics — the ones
 * that also painted tabs and the project tree red via WolfTheProblemSolver.
 *
 * The settings here point every Phpactor process (CLI, base container, language server) at
 * the plugin-managed STATIC index and make the scanner follow symlinks. The language server
 * session itself overrides `indexer.index_path` to a private LIVE copy (see
 * PhpactorConnectionProvider) because a running session rewrites index records and would
 * corrupt the static index.
 */
object PhpactorGlobalConfig {

    private val LOG = Logger.getInstance(PhpactorGlobalConfig::class.java)
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    fun configPath(): Path =
        Path.of(System.getProperty("user.home"), ".config", "phpactor", "phpactor.json")

    /**
     * Codes of diagnostics that are pure noise on legacy (non-Composer, swoole_system)
     * codebases: docblock/return-type nagging, undefined-variable warnings on the
     * auto-vivification idiom, and member-not-found — overwhelmingly false positives there
     * (protobuf `__call` accessors, env-variant config classes, dynamic mixins) AND the only
     * remaining ERROR-severity source, which is what paints tabs and the project tree red
     * via the raw-diagnostics Wolf marks. Removed from this list, a code can be re-enabled by
     * the user by setting `language_server.diagnostic_ignore_codes` themselves (user value
     * wins). Note the protobuf/variant class suppression in PhpClientFilters still narrows
     * member findings if worse.missing_member is re-enabled.
     */
    private val DEFAULT_IGNORED_DIAGNOSTIC_CODES = listOf(
        "worse.missing_member",
        "worse.docblock_missing_param",
        "worse.docblock_missing_return_type",
        "worse.missing_return_type",
        "worse.undefined_variable",
    )

    /**
     * Ensures the global config contains our indexer settings and default diagnostic-ignore
     * list, preserving any other keys the user may have set. Rewrites only when something
     * actually changed. A user-provided `language_server.diagnostic_ignore_codes` is never
     * overwritten.
     */
    fun ensure(indexSchemaVersion: Int) {
        runCatching {
            val path = configPath()
            Files.createDirectories(path.parent)
            val existing = if (Files.isRegularFile(path)) runCatching {
                JsonParser.parseString(Files.readString(path)).asJsonObject
            }.getOrNull() ?: JsonObject() else JsonObject()

            val wanted = JsonObject().apply {
                addProperty("indexer.follow_symlinks", true)
                addProperty("indexer.index_path", "%cache%/index/%project_id%-static-v$indexSchemaVersion")
                addProperty("indexer.max_filesize_to_index", PhpactorConnectionProvider.MAX_FILESIZE_TO_INDEX)
                add("language_server.diagnostic_ignore_codes", com.google.gson.JsonArray().apply {
                    DEFAULT_IGNORED_DIAGNOSTIC_CODES.forEach { add(it) }
                })
            }
            // The extracted stubs (with the generated Swoole aliases and the ProtobufMessage
            // stub) must also be visible to the base-container diagnostics pipeline, which
            // never sees the LSP initializationOptions — point its stub dir at our copy.
            val stubs = PhpactorManager.stubsDir()
            if (Files.isDirectory(stubs)) {
                wanted.addProperty("worse_reflection.stub_dir", stubs.toString())
            }

            var changed = false
            for ((key, value) in wanted.entrySet()) {
                // User-set ignore codes always win; everything else is kept in sync.
                if (key == "language_server.diagnostic_ignore_codes" && existing.has(key)) {
                    continue
                }
                if (existing.get(key) != value) {
                    existing.add(key, value)
                    changed = true
                }
            }
            if (changed) {
                Files.writeString(path, GSON.toJson(existing))
                LOG.info("php-portable: updated ${path}")
            }
        }.onFailure { LOG.warn("php-portable: could not update global Phpactor config", it) }
    }
}
