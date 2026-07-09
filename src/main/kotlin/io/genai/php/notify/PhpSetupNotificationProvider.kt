package io.genai.php.notify

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import io.genai.php.sdk.PhpInterpreterActions
import io.genai.php.settings.PhpInterpreterSettings
import io.genai.php.settings.PhpInterpretersConfigurable
import java.util.function.Function
import javax.swing.JComponent

/**
 * On a .php file, if no PHP interpreter is configured yet, show a banner offering to
 * download or add one (mirrors the IDE's own "install support" bar). The banner
 * disappears automatically once an interpreter exists.
 */
class PhpSetupNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.extension?.lowercase() !in PHP_EXTENSIONS) return null
        // Hide the banner once a usable interpreter exists (matches how runs resolve).
        if (PhpInterpreterSettings.getInstance().defaultSdk() != null) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text("No PHP interpreter configured — set one up to run this file.")
                createActionLabel("Download PHP…") {
                    PhpInterpreterActions.downloadInteractively(project) { refresh(project) }
                }
                createActionLabel("Add from Disk…") {
                    PhpInterpreterActions.addFromDisk { refresh(project) }
                }
                createActionLabel("Settings…") {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, PhpInterpretersConfigurable::class.java)
                }
            }
        }
    }

    private fun refresh(project: Project) {
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    companion object {
        private val PHP_EXTENSIONS = setOf("php", "phtml", "php5", "php7", "php8", "inc")
    }
}
