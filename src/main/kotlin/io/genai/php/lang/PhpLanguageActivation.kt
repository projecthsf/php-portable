package io.genai.php.lang

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binds our lightweight PHP file type to `.php*` files — but ONLY in an IDE that has no
 * official PHP support (i.e. IntelliJ IDEA Community, the whole reason this plugin exists).
 *
 * On PhpStorm / IDEA Ultimate the com.jetbrains.php plugin owns those extensions, so we stay
 * dormant: our `<fileType>` is declared with no extensions (see plugin.xml), meaning we never
 * statically claim `.php` and there is nothing to clash. We only ever *add* the association,
 * at runtime, when the official plugin is absent — using the supported
 * [FileTypeManager.associate] API (not the deprecated `registerFileType`).
 *
 * The plugin's tooling (portable PHP SDK, run configuration, and later composer / phpunit)
 * keys off the `.php` extension directly ([PhpFiles.EXTENSIONS]), so it keeps working in every
 * IDE regardless of who owns the file type.
 */
class PhpLanguageActivation : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Association is application-global; do it once per IDE session, not per project.
        if (!activated.compareAndSet(false, true)) return
        // Where PHP is already supported, do nothing — let the official plugin own .php.
        if (officialPhpPresent()) return

        val fileTypeManager = FileTypeManager.getInstance()
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                for (ext in PhpFiles.EXTENSIONS) {
                    fileTypeManager.associate(PhpFileType, ExtensionFileNameMatcher(ext))
                }
            }
        }
    }

    // Uses the public PluginManager facade, not the @ApiStatus.Internal PluginManagerCore.
    // findEnabledPlugin returns non-null only when the official PHP plugin is present AND
    // enabled — exactly the case where it owns .php and we must stay dormant.
    private fun officialPhpPresent(): Boolean =
        PluginManager.getInstance().findEnabledPlugin(PluginId.getId("com.jetbrains.php")) != null

    companion object {
        private val activated = AtomicBoolean(false)
    }
}
