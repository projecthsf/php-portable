package io.genai.php.sdk

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import org.jdom.Element
import java.io.File
import javax.swing.Icon

/**
 * The "PHP" SDK type. Downloading/switching PHP is handled by our own UI (the status-bar
 * switcher and Settings ▸ Portable PHP), so we keep this type out of the platform's
 * Java-oriented SDK combos: `allowCreationByUser() = false` removes both the "Add" and
 * "Download" actions there (the platform builds both from the same creatable-types list).
 */
class PhpSdkType : SdkType("PHP Portable") {

    override fun suggestHomePath(): String? = null

    override fun isValidSdkHome(path: String): Boolean = findPhpExecutable(path) != null

    override fun getVersionString(sdkHome: String): String? {
        val php = findPhpExecutable(sdkHome) ?: return null
        return try {
            val process = ProcessBuilder(php.absolutePath, "-r", "echo PHP_VERSION;")
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            out.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String {
        val version = getVersionString(sdkHome)
        return if (version != null) "PHP $version" else "PHP"
    }

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator,
    ): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    override fun getPresentableName(): String = "PHP Portable"

    override fun getIcon(): Icon = ICON

    // Keep PHP out of the platform's Add/Download SDK menus (we have our own UI).
    override fun allowCreationByUser(): Boolean = false

    companion object {
        private val ICON: Icon = IconLoader.getIcon("/icons/php.svg", PhpSdkType::class.java)

        fun getInstance(): PhpSdkType = SdkType.findInstance(PhpSdkType::class.java)

        /**
         * Locate the `php` binary within an SDK home, checking common layouts:
         * home root, home/bin, and one level of nesting (archives that expand into a
         * versioned subfolder).
         */
        fun findPhpExecutable(home: String?): File? {
            if (home.isNullOrBlank()) return null
            val root = File(home)
            if (!root.exists()) return null
            val names = listOf("php", "php.exe")
            val candidates = mutableListOf<File>()
            for (n in names) {
                candidates += File(root, n)
                candidates += File(File(root, "bin"), n)
            }
            root.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                for (n in names) {
                    candidates += File(sub, n)
                    candidates += File(File(sub, "bin"), n)
                }
            }
            return candidates.firstOrNull { it.isFile }
        }
    }
}
