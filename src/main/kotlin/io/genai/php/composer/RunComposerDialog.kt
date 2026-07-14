package io.genai.php.composer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Prompts for a Composer command: a dropdown of common commands, plus a package field that
 * only appears for commands that take a package argument (require / remove).
 */
class RunComposerDialog(project: Project) : DialogWrapper(project) {

    private val commandCombo = ComboBox(COMMANDS)
    private val packageField = JBTextField()
    private lateinit var packageRow: Row

    init {
        title = "Run Composer"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = panel {
            row("Command:") { cell(commandCombo) }
            row("Package:") {
                cell(packageField).align(AlignX.FILL)
                    .comment("e.g. symfony/console or vendor/pkg:^1.2")
            }.also { packageRow = it }
        }
        commandCombo.addActionListener { syncPackageRow() }
        syncPackageRow()
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = commandCombo

    private fun syncPackageRow() = packageRow.visible(needsPackage())

    private fun needsPackage(): Boolean = (commandCombo.selectedItem as? String) in PACKAGE_COMMANDS

    /** The Composer argv, e.g. ["install"] or ["require", "vendor/pkg"]. */
    val commandArgs: List<String>
        get() {
            val cmd = commandCombo.selectedItem as? String ?: return emptyList()
            val pkg = packageField.text.trim()
            return if (cmd in PACKAGE_COMMANDS && pkg.isNotEmpty()) listOf(cmd, pkg) else listOf(cmd)
        }

    companion object {
        private val COMMANDS = arrayOf(
            "install", "update", "dump-autoload", "require", "remove",
            "validate", "outdated", "show", "status",
        )
        private val PACKAGE_COMMANDS = setOf("require", "remove")
    }
}
