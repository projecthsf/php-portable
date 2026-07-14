package io.genai.php.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.util.io.HttpRequests
import io.genai.php.sdk.PhpSdkManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Locates (and, on request, downloads) the Phpactor PHAR — the PHP language server that
 * powers code intelligence. Phpactor is a single MIT-licensed `.phar` that runs *on the
 * portable PHP interpreter we already manage*, so it needs no separate runtime and works
 * fully offline once present. Stored next to the interpreters under ~/.php-portable.
 */
object PhpactorManager {

    // Pinned release for reproducibility; bump deliberately. Phpactor publishes phpactor.phar
    // on each (date-tagged) GitHub release. Verified this tag has the .phar asset.
    private const val VERSION = "2026.06.25.0"
    private const val DOWNLOAD_URL =
        "https://github.com/phpactor/phpactor/releases/download/$VERSION/phpactor.phar"

    /** ~/.php-portable/phpactor.phar */
    fun pharPath(): Path = PhpSdkManager.downloadRoot().resolve("phpactor.phar")

    fun isInstalled(): Boolean = Files.isRegularFile(pharPath())

    /** On-disk copy of phpstorm-stubs (extracted from the phar) so go-to-definition on PHP
     *  built-ins (\DateTime, json_encode, …) lands on real, openable files instead of the
     *  phar-internal path. Pointed at via Phpactor's stub config (see PhpactorConnectionProvider). */
    fun stubsDir(): Path = PhpSdkManager.downloadRoot().resolve("phpstorm-stubs")

    fun areStubsExtracted(): Boolean = Files.isDirectory(stubsDir())

    /**
     * Extract phpstorm-stubs from the phar to [stubsDir] using the given portable PHP — once,
     * ~0.7s. No-op if already present. This is what makes go-to-definition on built-ins open a
     * real file (see PhpactorConnectionProvider's stub config); Phpactor can't navigate into
     * its own phar-bundled stubs. Blocking; call off the EDT (connection-provider init is fine).
     */
    fun ensureStubsExtracted(php: File) {
        if (areStubsExtracted()) return
        val dest = stubsDir()
        Files.createDirectories(dest)
        val script =
            "\$p = new Phar(\$argv[1]);" +
                "\$pre = 'phar://' . \$argv[1] . '/vendor/jetbrains/phpstorm-stubs/';" +
                "foreach (new RecursiveIteratorIterator(\$p) as \$f) {" +
                "  \$x = \$f->getPathname();" +
                "  if (strpos(\$x, \$pre) !== 0) continue;" +
                "  \$t = \$argv[2] . '/' . substr(\$x, strlen(\$pre));" +
                "  @mkdir(dirname(\$t), 0777, true);" +
                "  @copy(\$x, \$t);" +
                "}"
        val cmd = GeneralCommandLine(php.absolutePath, "-r", script, pharPath().toString(), dest.toString())
        ExecUtil.execAndGetOutput(cmd)
    }

    /** Blocking download — call off the EDT. Returns the phar path on success. */
    fun download(): Path {
        val target = pharPath()
        Files.createDirectories(target.parent)
        HttpRequests.request(DOWNLOAD_URL).saveToFile(target.toFile(), null)
        return target
    }
}
