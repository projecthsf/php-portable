package io.genai.php.lsp

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable

/**
 * One-click setup for PHP code intelligence. Two prerequisites the user would otherwise have to
 * find and install by hand:
 *   1. Phpactor — a `.phar` we download ourselves (fast, fully automatic, in our control).
 *   2. LSP4IJ — a JetBrains Marketplace plugin. We open the Plugins screen so the user installs
 *      it with one click, then restarts.
 *
 * We deliberately don't drive the LSP4IJ install fully programmatically. The only APIs for that
 * (PluginsAdvertiser.installAndEnable, PluginManagerConfigurable.showPluginConfigurable) are
 * either in the frontend-split `app-client` module that isn't on the plugin classpath, or marked
 * `@ApiStatus.Internal` — using them fails the JetBrains Plugin Verifier. Opening the Plugins
 * screen via its public configurable id is the supported, verifier-clean path.
 *
 * [enable] chains both so the setup banner / settings button can offer a single action instead
 * of making the user piece the steps together.
 */
object CodeIntelligenceSetup {

    const val LSP4IJ_ID = "com.redhat.devtools.lsp4ij"

    /** Well-known id of the built-in Plugins settings page (public API, not the internal class). */
    private const val PLUGINS_CONFIGURABLE_ID = "preferences.pluginManager"

    fun isLsp4ijInstalled(): Boolean =
        PluginManager.isPluginInstalled(PluginId.getId(LSP4IJ_ID))

    /** Both prerequisites present — code intelligence can actually run. */
    fun isFullySetUp(): Boolean = isLsp4ijInstalled() && PhpactorManager.isInstalled()

    /**
     * Download Phpactor (if missing), then — if LSP4IJ isn't installed — open the Plugins screen
     * so the user installs it (search "LSP4IJ", Install, restart). [onChanged] runs after the
     * Phpactor step so callers can refresh their banners. Must be called on the EDT.
     */
    fun enable(project: Project, onChanged: () -> Unit) {
        if (!PhpactorManager.isInstalled()) {
            try {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    ThrowableComputable { PhpactorManager.download() },
                    "Downloading Phpactor…",
                    true,
                    project,
                )
                onChanged()
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to download Phpactor: ${e.message}",
                    "PHP Code Intelligence",
                )
                return
            }
        }

        if (!isLsp4ijInstalled()) {
            Messages.showInfoMessage(
                project,
                "Phpactor is ready. One step left: in the Plugins window that opens, go to " +
                    "<b>Marketplace</b>, search <b>LSP4IJ</b>, click <b>Install</b>, then restart the IDE.",
                "Enable PHP Code Intelligence",
            )
            ShowSettingsUtil.getInstance().showSettingsDialog(project, PLUGINS_CONFIGURABLE_ID)
        } else {
            onChanged()
        }
    }
}
