package io.genai.php.settings

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import io.genai.php.sdk.PhpInterpreterActions
import io.genai.php.sdk.PhpSdkManager
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JComponent

/**
 * Settings ▸ Languages & Frameworks ▸ Portable PHP — download, add-from-disk,
 * and remove portable PHP interpreters. Named "Portable PHP" (not "PHP Interpreters")
 * so it doesn't read as a duplicate of PhpStorm's own PHP settings when both are present.
 * Changes apply immediately (they mutate the shared SDK table), so there's no Apply/Reset
 * state to track.
 */
class PhpInterpretersConfigurable : Configurable {

    private val model = DefaultListModel<Sdk>()
    private val list = JBList(model)

    override fun getDisplayName(): String = "Portable PHP"

    override fun createComponent(): JComponent {
        list.cellRenderer = SimpleListCellRenderer.create { label, sdk, _ ->
            label.text = "${sdk.name}   —   ${sdk.homePath ?: "?"}"
        }
        reload()

        return panel {
            row {
                comment(
                    "Portable PHP interpreters. Downloads are stored under " +
                        "<code>~/.php-portable</code> and shared with PHP run configurations.",
                )
            }
            row {
                cell(JBScrollPane(list)).align(Align.FILL)
            }.resizableRow()
            row {
                button("Download PHP…") { downloadAction() }
                button("Add from Disk…") { addFromDiskAction() }
                button("Remove") { removeAction() }
                button("Clean Up") { cleanUpAction() }
                button("Open Folder") { openFolderAction() }
            }
        }
    }

    private fun reload() {
        model.clear()
        PhpSdkManager.listSdks().forEach { model.addElement(it) }
    }

    private fun downloadAction() = PhpInterpreterActions.downloadInteractively(null) { reload() }

    private fun addFromDiskAction() = PhpInterpreterActions.addFromDisk { reload() }

    private fun removeAction() {
        val sdk = list.selectedValue ?: return
        val home = sdk.homePath
        val managed = home != null &&
            File(home).absolutePath.startsWith(PhpSdkManager.downloadRoot().toFile().absolutePath + File.separator)

        val deleteFiles: Boolean
        if (managed) {
            val answer = Messages.showYesNoCancelDialog(
                "Remove interpreter \"${sdk.name}\"?\n\nIt was downloaded to $home.",
                "Remove PHP Interpreter",
                "Remove and Delete Files",
                "Remove Only",
                "Cancel",
                Messages.getQuestionIcon(),
            )
            when (answer) {
                Messages.YES -> deleteFiles = true
                Messages.NO -> deleteFiles = false
                else -> return
            }
        } else {
            val ok = Messages.showYesNoDialog(
                "Remove interpreter \"${sdk.name}\"?\n\n(Files on disk are left untouched.)",
                "Remove PHP Interpreter",
                Messages.getQuestionIcon(),
            )
            if (ok != Messages.YES) return
            deleteFiles = false
        }

        PhpSdkManager.remove(sdk, deleteFiles)
        reload()
    }

    private fun cleanUpAction() {
        val result = PhpSdkManager.cleanUp()
        reload()
        Messages.showInfoMessage(
            "Removed ${result.removed} missing interpreter(s); registered ${result.added} orphaned install(s).",
            "Clean Up PHP Interpreters",
        )
    }

    private fun openFolderAction() {
        val sdk = list.selectedValue
        val dir = sdk?.homePath?.let { File(it) } ?: PhpSdkManager.downloadRoot().toFile()
        if (dir.exists()) RevealFileAction.openDirectory(dir)
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        // Actions apply immediately; nothing to commit here.
    }
}
