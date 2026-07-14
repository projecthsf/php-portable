package io.genai.php.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import io.genai.php.sdk.PhpSdkType
import io.genai.php.settings.PhpInterpreterSettings
import java.nio.file.Files

/**
 * Launches Phpactor as `<portable-php> phpactor.phar language-server`, talking LSP over
 * stdio. The interpreter is the plugin's current default portable PHP, so code intelligence
 * reuses the exact runtime the user already runs scripts with — no extra dependency.
 *
 * If no interpreter or PHAR is available the command list is left empty and the server won't
 * start; [PhpClientFeatures.isEnabled] gates this up front so we don't get here in that case.
 */
class PhpactorConnectionProvider(project: Project) : ProcessStreamConnectionProvider() {
    init {
        val php = PhpInterpreterSettings.getInstance().defaultSdk()?.homePath
            ?.let { PhpSdkType.findPhpExecutable(it) }
        val phar = PhpactorManager.pharPath()
        if (php != null && PhpactorManager.isInstalled()) {
            // One-time (~0.7s): unpack phpstorm-stubs to disk so go-to-definition on built-ins
            // opens real files (see getInitializationOptions). No-op once extracted.
            PhpactorManager.ensureStubsExtracted(php)
            setCommands(listOf(php.absolutePath, phar.toString(), "language-server"))
            project.basePath?.let { setWorkingDirectory(it) }
        }
    }

    /**
     * Point Phpactor's stub config at our on-disk phpstorm-stubs (extracted from the phar),
     * replacing the default `phar://…` path. Phpactor merges initializationOptions into its
     * config, so go-to-definition on built-ins (\DateTime, json_encode, …) resolves to real,
     * openable files instead of a sealed phar path.
     */
    override fun getInitializationOptions(rootUri: VirtualFile?): Any? {
        val stubs = PhpactorManager.stubsDir()
        if (!Files.isDirectory(stubs)) return null
        return mapOf(
            "worse_reflection.stub_dir" to stubs.toString(),
            "indexer.stub_paths" to listOf(stubs.toString()),
        )
    }
}
