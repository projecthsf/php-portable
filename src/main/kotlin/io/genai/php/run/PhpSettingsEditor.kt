package io.genai.php.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.genai.php.sdk.PhpSdkType
import javax.swing.JComponent

class PhpSettingsEditor(project: Project) : SettingsEditor<PhpRunConfiguration>() {

    private val sdkCombo = ComboBox<String>()
    private val interpreterField = TextFieldWithBrowseButton()
    private val scriptField = TextFieldWithBrowseButton()

    init {
        sdkCombo.addItem(NONE)
        ProjectJdkTable.getInstance().getSdksOfType(PhpSdkType.getInstance()).forEach {
            sdkCombo.addItem(it.name)
        }
        scriptField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle("Select PHP Script"),
                project,
            ),
        )
        interpreterField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle("Select PHP Executable"),
                project,
            ),
        )
    }

    override fun createEditor(): JComponent = panel {
        row("PHP SDK:") {
            cell(sdkCombo).align(AlignX.FILL)
        }
        row("Or PHP executable:") {
            cell(interpreterField).align(AlignX.FILL)
        }.rowComment("Overrides the SDK above when set.")
        row("PHP script:") {
            cell(scriptField).align(AlignX.FILL)
        }
    }

    override fun resetEditorFrom(s: PhpRunConfiguration) {
        sdkCombo.selectedItem = s.sdkName?.takeIf { it.isNotBlank() } ?: NONE
        interpreterField.text = s.interpreterPath.orEmpty()
        scriptField.text = s.scriptPath.orEmpty()
    }

    override fun applyEditorTo(s: PhpRunConfiguration) {
        val selected = sdkCombo.selectedItem as? String
        s.sdkName = if (selected == null || selected == NONE) "" else selected
        s.interpreterPath = interpreterField.text.trim()
        s.scriptPath = scriptField.text.trim()
    }

    companion object {
        private const val NONE = "<none>"
    }
}
