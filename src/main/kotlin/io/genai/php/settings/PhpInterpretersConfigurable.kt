package io.genai.php.settings

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import io.genai.php.lsp.CodeIntelligenceSetup
import io.genai.php.lsp.PhpactorManager
import com.intellij.ui.ColoredListCellRenderer
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
        list.cellRenderer = object : ColoredListCellRenderer<Sdk>() {
            override fun customizeCellRenderer(
                list: javax.swing.JList<out Sdk>,
                sdk: Sdk,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                append("${sdk.name}   —   ${sdk.homePath ?: "?"}")
            }
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
            separator()
            row {
                checkBox("Code intelligence (completion, navigation, errors)")
                    .applyToComponent {
                        isSelected = PhpInterpreterSettings.getInstance().codeIntelligenceEnabled
                        addActionListener {
                            PhpInterpreterSettings.getInstance().codeIntelligenceEnabled = isSelected
                        }
                    }
                button(if (CodeIntelligenceSetup.isFullySetUp()) "Reinstall Phpactor…" else "Enable Code Intelligence…") {
                    enableCodeIntelligenceAction()
                }
            }.rowComment(
                "Runs a local PHP language server (Phpactor) on the selected interpreter — " +
                    "fully offline. Also installs the free <b>LSP4IJ</b> plugin (one click, may " +
                    "prompt a restart). Restart the server after changing these settings.",
            )
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

    /**
     * One-click enable from Settings: download Phpactor and install the LSP4IJ plugin. Needs a
     * project for the plugin-install flow; if somehow none is open, fall back to just fetching
     * Phpactor. When already fully set up, this button is a Phpactor re-download instead.
     */
    private fun enableCodeIntelligenceAction() {
        if (CodeIntelligenceSetup.isFullySetUp()) {
            downloadPhpactorAction()
            return
        }
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            downloadPhpactorAction()
            return
        }
        CodeIntelligenceSetup.enable(project) {}
    }

    private fun downloadPhpactorAction() {
        try {
            val path = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable { PhpactorManager.download() },
                "Downloading Phpactor…",
                true,
                null,
            )
            Messages.showInfoMessage(
                "Phpactor installed at $path.\nOpen a .php file to start code intelligence.",
                "Code Intelligence",
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Failed to download Phpactor: ${e.message}",
                "Code Intelligence",
            )
        }
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
