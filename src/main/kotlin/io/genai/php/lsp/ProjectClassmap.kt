package io.genai.php.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Generates a Composer-compatible class map for projects that have no Composer autoloader
 * (the swoole_system-style layout: custom AutoloadService, symlinked checkouts, protobuf
 * files with dozens of classes, lowercase snake_case filenames).
 *
 * Why: Phpactor's diagnostics pipeline resolves class declarations through the composer
 * autoloader and — absent one — falls back to a scanner that neither follows symlinks nor
 * understands "many classes per file" / snake_case names. The result is a flood of false
 * `Class "X" not found` diagnostics for perfectly valid project classes. Feeding Phpactor a
 * generated class map (via `composer.autoloader_path`, read in class-maps-only mode — no
 * project code is ever executed) repairs that side of the pipeline; the generated
 * `classes.txt` additionally powers the client-side diagnostic filter in [PhpClientFeatures]
 * for the (verified) cases Phpactor still cannot resolve.
 *
 * The map is regenerated at every language-server start (~0.5s for ~800 files, blocking) so
 * newly added classes are picked up. Real Composer projects (vendor/composer exists) are
 * skipped: their own autoloader already works and diagnostics are correct there.
 */
object ProjectClassmap {

    private val LOG = Logger.getInstance(ProjectClassmap::class.java)

    @Volatile
    private var cachedClasses: Set<String>? = null

    /** Root for generated maps: ~/.php-portable/classmaps/<project>-<hash6> */
    private fun outputDir(projectRoot: String): Path {
        val hash = MessageDigest.getInstance("MD5")
            .digest(projectRoot.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(6)
        val name = File(projectRoot).name.ifBlank { "project" }
        return Path.of(System.getProperty("user.home"), ".php-portable", "classmaps", "$name-$hash")
    }

    /**
     * Generates the class map for [projectRoot] using the SDK interpreter [php].
     * @return the autoloader paths to hand to Phpactor via `composer.autoloader_path`
     *         (the generated map plus, if present, the project's own vendor/autoload.php),
     *         or null when generation was skipped (Composer project) or failed.
     */
    fun generate(php: File, projectRoot: String): List<String>? {
        return runCatching {
            if (Files.isRegularFile(Path.of(projectRoot, "vendor", "composer", "autoload_classmap.php")) ||
                Files.isRegularFile(Path.of(projectRoot, "composer.json"))
            ) {
                return null // real Composer project: its autoloader already works
            }
            val scanner = extractScanner() ?: return null
            val out = outputDir(projectRoot)
            Files.createDirectories(out)
            val cmd = GeneralCommandLine(php.absolutePath, scanner.toString(), projectRoot, out.toString())
            val output = ExecUtil.execAndGetOutput(cmd, 120_000)
            if (output.exitCode != 0 || !Files.isRegularFile(out.resolve("autoload.php"))) {
                LOG.warn("php-portable: class map generation failed: ${output.stderr.take(300)}")
                return null
            }
            LOG.info("php-portable: class map generated: ${output.stdoutLines.lastOrNull() ?: ""}")
            cachedClasses = null // refresh on next knownClasses() read
            cachedProtobufClasses = null
            cachedVariantClasses = null
            buildList {
                add(out.resolve("autoload.php").toString())
                val vendor = Path.of(projectRoot, "vendor", "autoload.php")
                if (Files.isRegularFile(vendor)) add(vendor.toString())
            }
        }.getOrNull()
    }

    /** Extracts the bundled scanner script (kept in sync with the plugin version). */
    private fun extractScanner(): Path? {
        return runCatching {
            val target = Path.of(System.getProperty("user.home"), ".php-portable", "classmap-generator.php")
            javaClass.getResourceAsStream("/php/classmap-generator.php")?.use { input ->
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } ?: return null
            target
        }.getOrNull()
    }

    /**
     * All class FQNs declared across every generated map. Used to suppress false
     * "Class X not found" diagnostics for classes that genuinely exist in a project.
     */
    fun knownClasses(): Set<String> {
        cachedClasses?.let { return it }
        val names = readGeneratedList("classes.txt")
        cachedClasses = names
        return names
    }

    @Volatile
    private var cachedProtobufClasses: Set<String>? = null

    /**
     * FQNs of classes declared as `extends \ProtobufMessage` (the runtime-provided protobuf
     * base whose generated accessors are __call magic — invisible to static analysis).
     * Used to suppress false `Method "X" does not exist` findings on those classes.
     */
    fun protobufClasses(): Set<String> {
        cachedProtobufClasses?.let { return it }
        val names = readGeneratedList("protobuf-classes.txt")
        cachedProtobufClasses = names
        return names
    }

    @Volatile
    private var cachedVariantClasses: Set<String>? = null

    /**
     * FQNs declared in more than one file — env-variant config classes in this framework
     * (the autoloader loads one variant per environment). Any single-file reflection picks
     * one arbitrary variant, so member findings on them are unreliable.
     */
    fun variantClasses(): Set<String> {
        cachedVariantClasses?.let { return it }
        val names = readGeneratedList("variant-classes.txt")
        cachedVariantClasses = names
        return names
    }

    private fun readGeneratedList(fileName: String): Set<String> {
        return runCatching {
            val root = Path.of(System.getProperty("user.home"), ".php-portable", "classmaps")
            if (!Files.isDirectory(root)) return@runCatching emptySet()
            val result = mutableSetOf<String>()
            Files.list(root).use { dirs ->
                dirs.filter { Files.isDirectory(it) }.forEach { dir ->
                    val file = dir.resolve(fileName)
                    if (Files.isRegularFile(file)) {
                        Files.readAllLines(file).forEach { if (it.isNotBlank()) result.add(it.trim()) }
                    }
                }
            }
            result
        }.getOrDefault(emptySet())
    }
}
