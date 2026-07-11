package io.genai.php.sdk

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch

/**
 * Portable PHP catalog, fetched live so the version list is always current:
 *   - macOS / Linux: static single-binary CLI builds from static-php-cli
 *     (https://dl.static-php.dev), one .tar.gz per version.
 *   - Windows: official .zip builds from windows.php.net.
 * Falls back to a tiny built-in list if the network is unavailable.
 *
 * We use the **bulk** preset, not `common`: these are *static* binaries (extensions are
 * compiled in and cannot be added later via pecl/.so), and `common` omits intl, gd, soap,
 * imagick, etc. Real projects fail `composer install` on a missing ext (e.g. ext-intl).
 * The bulk build carries ~65 extensions — the price is a larger download (~35 MB), which is
 * fine for a dev tool and worth it to make "run composer / phpunit locally" actually work.
 */
object PhpDownloads {

    private const val STATIC_PHP_BASE = "https://dl.static-php.dev/static-php-cli/bulk/"
    private const val WINDOWS_BASE = "https://windows.php.net/downloads/releases/"

    fun currentOs(): OsFamily = when {
        SystemInfo.isWindows -> OsFamily.WINDOWS
        SystemInfo.isMac -> OsFamily.MAC
        else -> OsFamily.LINUX
    }

    private fun currentArch(): String = if (CpuArch.isArm64()) "aarch64" else "x86_64"

    /** Fetch the catalog on a background thread, showing a modal progress dialog. */
    fun fetchAvailableWithProgress(project: Project?): List<PhpRelease> =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable { fetchAvailable() },
            "Fetching Available PHP Versions…",
            true,
            project,
        )

    /** Blocking fetch — must be called off the EDT. Returns newest-first. */
    fun fetchAvailable(): List<PhpRelease> = try {
        when (currentOs()) {
            OsFamily.MAC -> parseStaticPhp("macos", currentArch())
            OsFamily.LINUX -> parseStaticPhp("linux", currentArch())
            OsFamily.WINDOWS -> parseWindows()
        }.ifEmpty { fallback() }
    } catch (e: Exception) {
        fallback()
    }

    private fun parseStaticPhp(osTag: String, arch: String): List<PhpRelease> {
        val html = HttpRequests.request(STATIC_PHP_BASE).readString()
        val regex = Regex("""php-(\d+\.\d+\.\d+)-cli-$osTag-$arch\.tar\.gz""")
        return regex.findAll(html).map { it.groupValues[1] }.distinct().toList()
            .sortedWith(VERSION_DESC)
            .map { v ->
                PhpRelease(v, currentOs(), arch, "$STATIC_PHP_BASE" + "php-$v-cli-$osTag-$arch.tar.gz", ArchiveKind.TAR_GZ)
            }
    }

    private fun parseWindows(): List<PhpRelease> {
        val html = HttpRequests.request(WINDOWS_BASE).readString()
        // Thread-safe x64 builds (skip the "-nts-" non-thread-safe variants).
        val regex = Regex("""php-(\d+\.\d+\.\d+)-Win32-vs\d+-x64\.zip""")
        return regex.findAll(html)
            .map { it.groupValues[1] to it.value }.distinctBy { it.first }.toList()
            .sortedWith(Comparator { x, y -> VERSION_DESC.compare(x.first, y.first) })
            .map { (v, file) -> PhpRelease(v, OsFamily.WINDOWS, "x64", "$WINDOWS_BASE$file", ArchiveKind.ZIP) }
    }

    private fun fallback(): List<PhpRelease> = when (currentOs()) {
        OsFamily.MAC -> listOf(
            PhpRelease("8.3.32", OsFamily.MAC, currentArch(),
                "$STATIC_PHP_BASE" + "php-8.3.32-cli-macos-${currentArch()}.tar.gz", ArchiveKind.TAR_GZ),
        )
        OsFamily.LINUX -> listOf(
            PhpRelease("8.3.32", OsFamily.LINUX, "x86_64",
                "$STATIC_PHP_BASE" + "php-8.3.32-cli-linux-x86_64.tar.gz", ArchiveKind.TAR_GZ),
        )
        OsFamily.WINDOWS -> listOf(
            PhpRelease("8.3.14", OsFamily.WINDOWS, "x64",
                "${WINDOWS_BASE}php-8.3.14-Win32-vs16-x64.zip", ArchiveKind.ZIP),
        )
    }

    /** Descending semantic-ish version order (8.5.8 before 8.3.14). */
    private val VERSION_DESC: Comparator<String> = Comparator<String> { a, b ->
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return@Comparator d
        }
        0
    }.reversed()
}
