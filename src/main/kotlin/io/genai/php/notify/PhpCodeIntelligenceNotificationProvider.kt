package io.genai.php.notify

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import io.genai.php.lang.PhpFileType
import io.genai.php.lang.PhpFiles
import io.genai.php.lsp.CodeIntelligenceSetup
import io.genai.php.sdk.PhpSdkType
import io.genai.php.settings.PhpInterpretersConfigurable
import io.genai.php.settings.PhpInterpreterSettings
import java.util.function.Function
import javax.swing.JComponent

/**
 * On a `.php` file where our language layer is active (IDEs without native PHP support), offer a
 * single click to turn on code intelligence. Without the two prerequisites (the LSP4IJ plugin and
 * the Phpactor language server) completion / go-to-definition just silently don't work and there's
 * no hint why. "Enable code intelligence" installs both via [CodeIntelligenceSetup]. Disappears
 * once both are present.
 *
 * The interpreter step is handled separately by [PhpSetupNotificationProvider]; this banner only
 * appears once an interpreter exists.
 */
class PhpCodeIntelligenceNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.extension?.lowercase() !in PhpFiles.EXTENSIONS) return null
        // Only where OUR file type owns .php (Community / GoLand / …). On PhpStorm the native
        // PHP support handles code intelligence, so we stay quiet.
        if (FileTypeManager.getInstance().getFileTypeByExtension("php") != PhpFileType) return null

        val settings = PhpInterpreterSettings.getInstance()
        if (!settings.codeIntelligenceEnabled) return null
        // Need an interpreter first; the setup banner drives that step.
        val hasInterpreter = settings.defaultSdk()?.homePath
            ?.let { PhpSdkType.findPhpExecutable(it) } != null
        if (!hasInterpreter) return null

        if (CodeIntelligenceSetup.isFullySetUp()) return null // fully set up

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text("Turn on PHP code intelligence — completion, go-to-definition and error highlighting.")
                createActionLabel("Enable code intelligence") {
                    CodeIntelligenceSetup.enable(project) {
                        EditorNotifications.getInstance(project).updateAllNotifications()
                    }
                }
                createActionLabel("Settings…") {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, PhpInterpretersConfigurable::class.java)
                }
            }
        }
    }
}
