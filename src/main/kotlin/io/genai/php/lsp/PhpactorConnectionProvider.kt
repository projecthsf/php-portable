package io.genai.php.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import io.genai.php.sdk.PhpSdkType
import io.genai.php.settings.PhpInterpreterSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * Launches Phpactor as `<portable-php> phpactor.phar language-server`, talking LSP over
 * stdio. The interpreter is the plugin's current default portable PHP, so code intelligence
 * reuses the exact runtime the user already runs scripts with — no extra dependency.
 *
 * If no interpreter or PHAR is available the command list is left empty and the server won't
 * start; [PhpClientFeatures.isEnabled] gates this up front so we don't get here in that case.
 */
class PhpactorConnectionProvider(project: Project) : ProcessStreamConnectionProvider() {

    /** Autoloader paths injected via `composer.autoloader_path` (set in init). */
    private var autoloaderPaths: List<String>? = null

    init {
        val php = PhpInterpreterSettings.getInstance().defaultSdk()?.homePath
            ?.let { PhpSdkType.findPhpExecutable(it) }
        val phar = PhpactorManager.pharPath()
        if (php != null && PhpactorManager.isInstalled()) {
            // One-time (~0.7s): unpack phpstorm-stubs to disk so go-to-definition on built-ins
            // opens real files (see getInitializationOptions). No-op once extracted.
            PhpactorManager.ensureStubsExtracted(php)
            // Also one-time: generate Swoole legacy-alias stubs (swoole_table, Co\*, …) into
            // the extracted stubs, so swoole_system-style projects can navigate/complete them.
            SwooleAliases.ensureGenerated(PhpactorManager.stubsDir())
            // Install the \ProtobufMessage base-class stub (declared API of the runtime
            // protobuf base that legacy message classes extend — without it every inherited
            // method is a false "does not exist"). No-op when already current.
            ensureProtobufMessageStub()
            setCommands(listOf(php.absolutePath, phar.toString(), "language-server"))
            project.basePath?.let { base ->
                setWorkingDirectory(base)
                // Global config FIRST: Phpactor's diagnostics pipeline runs in a base container
                // that only reads config FILES (never initializationOptions) — without the
                // global file it resolves classes with a symlink-blind scanner and floods
                // tabs/tree/editor with false "Class not found" (see PhpactorGlobalConfig).
                PhpactorGlobalConfig.ensure(INDEX_SCHEMA_VERSION)
                // Class map for non-Composer projects (see ProjectClassmap): repairs class
                // resolution in Phpactor's session pipeline (~0.5s, blocking on purpose —
                // the map must be ready before the first handshake).
                autoloaderPaths = ProjectClassmap.generate(php, base)
                // Build the STATIC index synchronously (incremental: ~8s cold, ~0s warm),
                // then give the language-server session its own LIVE copy. This separation is
                // the core fix for the recurring "Class not found":
                //  - diagnostics (base container) read the STATIC index, written only here —
                //    single writer, records always complete;
                //  - the running session rewrites index records as files are opened/edited
                //    (verified: it corrupts shared indexes even on graceful shutdown), so it
                //    must never write the static one.
                warmUpIndex(php, phar, base)
                copyStaticIndexToLive(base)
            }
        }
    }

    /**
     * Copies the bundled `\ProtobufMessage` stub into the extracted stubs dir. The stub
     * changes rarely; when it does, the WorseReflection stub-map caches must be dropped so
     * they rebuild (same mechanism as SwooleAliases).
     */
    private fun ensureProtobufMessageStub() {
        runCatching {
            val target = PhpactorManager.stubsDir().resolve("protobuf_message.php")
            val stub = javaClass.getResourceAsStream("/php/protobuf_message_stub.php")?.use {
                it.readBytes().decodeToString()
            } ?: return
            if (Files.isRegularFile(target) && Files.readString(target) == stub) return
            Files.writeString(target, stub)
            // Invalidate the WorseReflection stub maps so the new file is picked up.
            val mapDir = Path.of(System.getProperty("user.home"), ".cache", "phpactor", "worse-reflection")
            if (Files.isDirectory(mapDir)) {
                Files.list(mapDir).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".map") }
                        .forEach { Files.deleteIfExists(it) }
                }
            }
            LOG.info("php-portable: ProtobufMessage stub installed")
        }.onFailure { LOG.warn("php-portable: ProtobufMessage stub install failed", it) }
    }

    /**
     * Builds the STATIC project index offline before the server starts. Idempotent:
     * `index:build` is incremental and exits almost immediately on a complete index.
     * The CLI needs no `--config-extra` — the global config (PhpactorGlobalConfig) carries
     * the settings.
     */
    private fun warmUpIndex(php: java.io.File, phar: java.nio.file.Path, base: String) {
        runCatching {
            val cmd = com.intellij.execution.configurations.GeneralCommandLine(
                php.absolutePath,
                phar.toString(),
                "index:build",
            ).withWorkDirectory(java.io.File(base))
            val output = com.intellij.execution.util.ExecUtil.execAndGetOutput(cmd, 300_000)
            LOG.info("php-portable: index:build finished (${output.exitCode}) ${output.stdoutLines.lastOrNull() ?: ""}")
        }.onFailure { LOG.warn("php-portable: index:build failed", it) }
    }

    /**
     * Refreshes the session's LIVE index (`<id>-live-<v>`) from the static build: the session
     * starts with a complete, consistent index and can then update its own copy freely.
     */
    private fun copyStaticIndexToLive(base: String) {
        runCatching {
            val cache = Path.of(System.getProperty("user.home"), ".cache", "phpactor", "index")
            val id = PhpactorManager.projectId(base)
            val static = cache.resolve("$id-static-$INDEX_SCHEMA_VERSION")
            val live = cache.resolve("$id-live-$INDEX_SCHEMA_VERSION")
            if (!Files.isDirectory(static)) return
            if (Files.exists(live)) {
                live.toFile().deleteRecursively()
            }
            val rc = ProcessBuilder("cp", "-R", static.toString(), live.toString()).start().waitFor()
            LOG.info("php-portable: live index refreshed (cp rc=$rc)")
        }.onFailure { LOG.warn("php-portable: live index refresh failed", it) }
    }

    /**
     * Point Phpactor's stub config at our on-disk phpstorm-stubs (extracted from the phar),
     * replacing the default `phar://…` path. Phpactor merges initializationOptions into its
     * config, so go-to-definition on built-ins (\DateTime, json_encode, …) resolves to real,
     * openable files instead of a sealed phar path.
     *
     * The indexer options are load-bearing for real-world projects: Phpactor's file scanner
     * does NOT follow symlinks by default, so a workspace whose root is a container of
     * symlinked checkouts (very common) indexes ZERO project files — member completion
     * returns nothing and every cross-file class shows as "Class not found". Turning
     * `indexer.follow_symlinks` on fixes both. `max_filesize_to_index` is raised above the
     * 1 MB default so large generated files (protobuf `pb_proto_*.php` in this stack can be
     * big) are indexed too.
     *
     * The on-disk index is keyed by project root only (`~/.cache/phpactor/index/<root>-<hash>`),
     * so an index poisoned by a previous run (built without the options above) would be reused
     * forever — `isFresh` skips every unchanged file and the missing class records never come
     * back. Versioning the path forces one clean rebuild whenever [INDEX_SCHEMA_VERSION] is
     * bumped; `%cache%` / `%project_id%` are expanded by Phpactor's path resolver.
     */
    override fun getInitializationOptions(rootUri: VirtualFile?): Any? {
        val options = linkedMapOf<String, Any>()
        // Session-only overrides on top of the global config (PhpactorGlobalConfig): the
        // session MUST NOT write the static index — it gets its own live copy, refreshed
        // from the static build at every server start (see copyStaticIndexToLive).
        options["indexer.index_path"] = "%cache%/index/%project_id%-live-$INDEX_SCHEMA_VERSION"
        val stubs = PhpactorManager.stubsDir()
        if (Files.isDirectory(stubs)) {
            options["worse_reflection.stub_dir"] = stubs.toString()
            options["indexer.stub_paths"] = listOf(stubs.toString())
        }
        // Class map (see ProjectClassmap): read by Phpactor in class-maps-only mode, no
        // project code is executed.
        autoloaderPaths?.let { options["composer.autoloader_path"] = it }
        return options
    }

    companion object {
        private val LOG = Logger.getInstance(PhpactorConnectionProvider::class.java)

        /**
         * Bump when the index layout/injected Phpactor config changes — forces one clean
         * rebuild of the static index and a fresh live copy.
         */
        const val INDEX_SCHEMA_VERSION = 6

        /** Above the 1 MB default: keep big generated files (protobuf, config dumps) indexed. */
        const val MAX_FILESIZE_TO_INDEX = 2_000_000
    }
}
