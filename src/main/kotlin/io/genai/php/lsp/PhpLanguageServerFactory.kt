package io.genai.php.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * Registers the PHP language server (Phpactor) with LSP4IJ. Declared via the LSP4IJ
 * `server` + `languageMapping` extension points in META-INF/lsp.xml (an optional module that
 * loads only when LSP4IJ is installed). LSP4IJ maps the server's LSP responses to IntelliJ's
 * native completion, navigation, hover and error highlighting.
 */
class PhpLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        PhpactorConnectionProvider(project)

    override fun createClientFeatures(): LSPClientFeatures = PhpClientFeatures()
}
