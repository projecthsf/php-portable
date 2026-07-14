package io.genai.php.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPCodeActionFeature
import com.redhat.devtools.lsp4ij.client.features.LSPDocumentLinkFeature
import io.genai.php.sdk.PhpSdkType
import io.genai.php.settings.PhpInterpreterSettings

/**
 * Gates when the PHP language server is active. LSP4IJ calls [isEnabled] before starting the
 * server for a file, so this is where the "Code intelligence" toggle and the prerequisites
 * (an interpreter is configured, the Phpactor PHAR is present) are enforced. Returning false
 * keeps the server dormant — no process, no indexing.
 *
 * Community-vs-PhpStorm is handled elsewhere for free: LSP4IJ maps this server to our
 * `PhpPortable` file type, which only owns `.php` on IDEs without the official PHP plugin.
 */
class PhpClientFeatures : LSPClientFeatures() {

    init {
        // Phpactor's document links render as noisy full-line underlines under Cmd+hover in
        // LSP4IJ. They only make include/require paths clickable, which isn't worth the noise —
        // completion, hover and go-to-definition are unaffected.
        setDocumentLinkFeature(object : LSPDocumentLinkFeature() {
            override fun isEnabled(file: PsiFile): Boolean = false
        })
        // Disable code actions. LSP4IJ's lightbulb continuously polls textDocument/codeAction,
        // which with Phpactor triggered a crash/retry storm (Amp ClosedException on a dead
        // process stream) — flooding errors and hanging on "Resolving code actions". Quick-fixes
        // are the least-essential feature here; dropping them keeps completion/nav/hover stable.
        setCodeActionFeature(object : LSPCodeActionFeature() {
            override fun isEnabled(file: PsiFile): Boolean = false
        })
    }

    override fun isEnabled(file: VirtualFile): Boolean {
        val settings = PhpInterpreterSettings.getInstance()
        if (!settings.codeIntelligenceEnabled) return false
        val hasInterpreter = settings.defaultSdk()?.homePath
            ?.let { PhpSdkType.findPhpExecutable(it) } != null
        return hasInterpreter && PhpactorManager.isInstalled()
    }
}
