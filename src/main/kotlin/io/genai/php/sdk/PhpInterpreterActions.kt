package io.genai.php.sdk

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages

/**
 * Interactive install flows shared by the settings panel and the editor banner.
 * All entry points run on the EDT; [onComplete] fires on the EDT after registration.
 */
object PhpInterpreterActions {

    fun downloadInteractively(project: Project?, onComplete: (Sdk?) -> Unit) {
        val releases = PhpDownloads.fetchAvailableWithProgress(project)
        if (releases.isEmpty()) {
            Messages.showInfoMessage("No portable PHP builds are listed for this OS.", "Download PHP")
            return
        }
        val dialog = PhpDownloadDialog(releases)
        if (!dialog.showAndGet()) return
        val release = dialog.selected ?: return
        val home = PhpSdkManager.plannedHome(release.version)

        ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading PHP ${release.version}", true) {
            override fun run(indicator: ProgressIndicator) {
                PhpSdkDownloadTask(release, home).doDownload(indicator)
            }

            override fun onSuccess() {
                val sdk = PhpSdkManager.registerFromHome(home.toString())
                if (sdk == null) {
                    Messages.showErrorDialog(
                        "Download finished but no php executable was found under\n$home",
                        "Download PHP",
                    )
                }
                onComplete(sdk)
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(error.message ?: error.toString(), "Download PHP Failed")
            }
        })
    }

    fun addFromDisk(onComplete: (Sdk?) -> Unit) {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select PHP Home Directory")
            .withDescription("Pick a folder that contains the php executable (or a bin/ with it).")
        val chosen = FileChooser.chooseFile(descriptor, null, null) ?: return
        val home = chosen.path
        if (PhpSdkType.findPhpExecutable(home) == null) {
            Messages.showErrorDialog("No php executable found under\n$home", "Add PHP Interpreter")
            return
        }
        onComplete(PhpSdkManager.registerFromHome(home))
    }
}
