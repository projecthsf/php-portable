package io.genai.php.lang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
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
        // If .php is already owned (e.g. by the official PHP plugin on PhpStorm), stay dormant.
        if (phpExtensionAlreadyOwned()) return

        val fileTypeManager = FileTypeManager.getInstance()
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                for (ext in PhpFiles.EXTENSIONS) {
                    fileTypeManager.associate(PhpFileType, ExtensionFileNameMatcher(ext))
                }
            }
        }
    }

    /**
     * Public, plugin-agnostic check: is `.php` already claimed by some other file type? On
     * PhpStorm / IDEA Ultimate the official PHP plugin registers it at load — before this
     * startup activity runs — so we defer to whoever owns it. `UnknownFileType` means nobody
     * owns it yet (Community); our own [PhpFileType] means we already bound it in a prior
     * session (re-associating is harmless). Avoids the @ApiStatus.Internal plugin-manager APIs.
     */
    private fun phpExtensionAlreadyOwned(): Boolean {
        val existing = FileTypeManager.getInstance().getFileTypeByExtension("php")
        return existing != UnknownFileType.INSTANCE && existing != PhpFileType
    }

    companion object {
        private val activated = AtomicBoolean(false)
    }
}
