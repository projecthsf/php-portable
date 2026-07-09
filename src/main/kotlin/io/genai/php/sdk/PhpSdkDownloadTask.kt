package io.genai.php.sdk

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads a portable PHP build into [homeDir] and extracts it. IntelliJ registers the
 * result as an SDK whose home is [homeDir]; [PhpSdkType.findPhpExecutable] then locates
 * the `php` binary within it (handles both root and nested archive layouts).
 */
class PhpSdkDownloadTask(
    private val release: PhpRelease,
    private val homeDir: Path,
) : SdkDownloadTask {

    override fun getSuggestedSdkName(): String = "PHP ${release.version}"
    override fun getPlannedHomeDir(): String = homeDir.toString()
    override fun getPlannedVersion(): String = release.version

    override fun doDownload(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        indicator.text = "Downloading PHP ${release.version}…"
        Files.createDirectories(homeDir)

        when (release.kind) {
            ArchiveKind.RAW_BINARY -> {
                val exe = homeDir.resolve(if (release.os == OsFamily.WINDOWS) "php.exe" else "php")
                HttpRequests.request(release.url).saveToFile(exe.toFile(), indicator)
                exe.toFile().setExecutable(true)
            }
            ArchiveKind.ZIP -> extractArchive(indicator, ".zip") { tmp ->
                Decompressor.Zip(tmp).extract(homeDir)
            }
            ArchiveKind.TAR_GZ -> extractArchive(indicator, ".tar.gz") { tmp ->
                Decompressor.Tar(tmp).extract(homeDir)
            }
        }
    }

    private fun extractArchive(indicator: ProgressIndicator, suffix: String, extract: (Path) -> Unit) {
        val tmp = Files.createTempFile("php-", suffix)
        try {
            HttpRequests.request(release.url).saveToFile(tmp.toFile(), indicator)
            indicator.text = "Extracting PHP ${release.version}…"
            extract(tmp)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
