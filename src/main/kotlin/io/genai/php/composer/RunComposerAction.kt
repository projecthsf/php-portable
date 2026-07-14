package io.genai.php.composer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.RunContentExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.genai.php.sdk.PhpSdkType
import io.genai.php.settings.PhpInterpreterSettings
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Runs a Composer command with the portable PHP interpreter — `<php> composer.phar <args>` —
 * in a Run console, using the project directory as the working dir. No system PHP/Composer
 * needed. composer.phar is resolved project-locally or from a managed download ([ComposerManager]).
 */
class RunComposerAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val base = project.basePath ?: run {
            Messages.showErrorDialog(project, "No project directory.", "Run Composer")
            return
        }
        val workDir = Path(base)

        val php = PhpInterpreterSettings.getInstance().defaultSdk()?.homePath
            ?.let { PhpSdkType.findPhpExecutable(it) }
        if (php == null) {
            Messages.showErrorDialog(
                project,
                "No PHP interpreter configured. Set one up in Settings ▸ Portable PHP.",
                "Run Composer",
            )
            return
        }

        val composer = ComposerManager.resolve(workDir) ?: run {
            val ok = Messages.showYesNoDialog(
                project,
                "No composer.phar found in the project or ~/.php-portable.\nDownload the latest Composer now?",
                "Run Composer",
                Messages.getQuestionIcon(),
            )
            if (ok != Messages.YES) return
            try {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    ThrowableComputable { ComposerManager.download() },
                    "Downloading Composer…",
                    true,
                    project,
                )
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to download Composer: ${ex.message}", "Run Composer")
                return
            }
        }

        val dialog = RunComposerDialog(project)
        if (!dialog.showAndGet()) return
        val args = dialog.commandArgs
        if (args.isEmpty()) return

        runComposer(project, php.absolutePath, composer.toString(), args, workDir.toString())
    }

    private fun runComposer(
        project: Project,
        php: String,
        composerPhar: String,
        args: List<String>,
        workDir: String,
    ) {
        val cmd = GeneralCommandLine()
            .withExePath(php)
            .withParameters(buildList { add(composerPhar); addAll(args) })
            .withWorkDirectory(workDir)
        val handler = OSProcessHandler(cmd)
        // Composer writes files (vendor/, lock, autoload) via an external process, which the
        // IDE's VFS won't notice on its own — refresh the project dir when it finishes so new
        // files (e.g. vendor/) show up without a manual "Reload from Disk". Async → EDT-safe.
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                LocalFileSystem.getInstance().findFileByNioFile(Path(workDir))?.let {
                    VfsUtil.markDirtyAndRefresh(true, true, true, it)
                }
            }
        })
        RunContentExecutor(project, handler)
            .withTitle("Composer ${args.joinToString(" ")}")
            .withActivateToolWindow(true)
            .run()
    }
}
