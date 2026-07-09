package io.genai.php.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import io.genai.php.sdk.PhpInterpreterActions
import io.genai.php.sdk.PhpSdkManager
import io.genai.php.settings.PhpInterpreterSettings

/**
 * Status-bar widget showing the current PHP version. Clicking it lists all installed
 * interpreters (current one checked) plus "Add more…" to download another.
 */
class PhpStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // Refresh the label when interpreters are added/removed anywhere in the IDE.
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(
                ProjectJdkTable.JDK_TABLE_TOPIC,
                object : ProjectJdkTable.Listener {
                    override fun jdkAdded(jdk: Sdk) = update()
                    override fun jdkRemoved(jdk: Sdk) = update()
                    override fun jdkNameChanged(jdk: Sdk, previousName: String) = update()
                },
            )
    }

    override fun dispose() {}

    // --- MultipleTextValuesPresentation ---

    override fun getSelectedValue(): String {
        val sdk = PhpInterpreterSettings.getInstance().defaultSdk() ?: return "No PHP"
        return sdk.versionString?.let { "PHP $it" } ?: sdk.name
    }

    override fun getTooltipText(): String = "PHP interpreter — click to switch version"

    override fun getPopupStep(): ListPopup {
        val settings = PhpInterpreterSettings.getInstance()
        val current = settings.defaultSdk()
        val items = buildList {
            PhpSdkManager.listSdks().forEach { add(Item.Version(it)) }
            add(Item.AddMore)
        }

        val step = object : BaseListPopupStep<Item>("PHP Interpreter", items) {
            override fun getTextFor(value: Item): String = when (value) {
                is Item.Version -> {
                    val mark = if (value.sdk.name == current?.name) "✓ " else "   "
                    mark + (value.sdk.versionString?.let { "PHP $it" } ?: value.sdk.name) +
                        "   (${value.sdk.homePath})"
                }
                Item.AddMore -> "＋ Add more…"
            }

            override fun isSpeedSearchEnabled(): Boolean = true

            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep {
                    when (selectedValue) {
                        is Item.Version -> {
                            settings.defaultSdkName = selectedValue.sdk.name
                            update()
                        }
                        Item.AddMore ->
                            PhpInterpreterActions.downloadInteractively(project) { newSdk ->
                                // Switch to the freshly downloaded version right away.
                                newSdk?.let { settings.defaultSdkName = it.name }
                                update()
                            }
                    }
                }
        }
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    private fun update() {
        statusBar?.updateWidget(ID())
    }

    private sealed interface Item {
        data class Version(val sdk: Sdk) : Item
        object AddMore : Item
    }

    companion object {
        const val WIDGET_ID = "PhpPortable.VersionWidget"
    }
}
