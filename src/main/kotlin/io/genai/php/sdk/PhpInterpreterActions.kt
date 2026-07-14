package io.genai.php.sdk

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files

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
        // Start in ~/.php-portable, where downloaded interpreters live, instead of the
        // last-used folder. Use the non-refreshing lookup: this runs on the EDT and a VFS
        // refresh would be a prohibited slow operation. Best-effort — if the dir isn't in the
        // VFS yet, the chooser just falls back to its default location.
        val root = PhpSdkManager.downloadRoot()
        Files.createDirectories(root)
        val toSelect = LocalFileSystem.getInstance().findFileByNioFile(root)
        val chosen = FileChooser.chooseFile(descriptor, null, toSelect) ?: return
        val home = chosen.path
        if (PhpSdkType.findPhpExecutable(home) == null) {
            Messages.showErrorDialog("No php executable found under\n$home", "Add PHP Interpreter")
            return
        }
        onComplete(PhpSdkManager.registerFromHome(home))
    }
}
