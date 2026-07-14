package io.genai.php.composer

import com.intellij.util.io.HttpRequests
import io.genai.php.sdk.PhpSdkManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Locates (or downloads) a `composer.phar` to run with the portable PHP. Prefers a
 * project-local `composer.phar` (many projects vendor one); otherwise falls back to a
 * managed copy under ~/.php-portable, downloadable on demand.
 */
object ComposerManager {

    private const val DOWNLOAD_URL = "https://getcomposer.org/download/latest-stable/composer.phar"

    /** ~/.php-portable/composer.phar — the managed fallback. */
    fun managedPhar(): Path = PhpSdkManager.downloadRoot().resolve("composer.phar")

    /**
     * The composer.phar to use for [projectDir]: a project-local one if present, else the
     * managed copy if it has been downloaded. Null when neither exists yet.
     */
    fun resolve(projectDir: Path): Path? {
        val local = projectDir.resolve("composer.phar")
        if (Files.isRegularFile(local)) return local
        val managed = managedPhar()
        return if (Files.isRegularFile(managed)) managed else null
    }

    /** Blocking download of the managed composer.phar — call off the EDT. */
    fun download(): Path {
        val target = managedPhar()
        Files.createDirectories(target.parent)
        HttpRequests.request(DOWNLOAD_URL).saveToFile(target.toFile(), null)
        return target
    }
}
