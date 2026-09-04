package io.genai.php.diag

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.lang.findUsages.LanguageFindUsages
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager
import com.redhat.devtools.lsp4ij.usages.LSPFindUsagesHandlerFactory
import io.genai.php.lsp.PhpTargetElementEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Developer-only end-to-end diagnostics for PHP navigation (Find Usages / callers popup).
 *
 * Inert unless the IDE is started with `-Dphp.portable.diagnose=true` (e.g.
 * `./gradlew runIde -Pphp.diag`). When active, it opens `<project>/main.php`, places the
 * caret on the `credentialWhere` declaration, and logs (logger `php-diag`) the state of every
 * gate the Find-Usages path goes through:
 * file/language support, the caret word range, `GotoDeclarationAction.findElementToShowUsagesOf`
 * (null ⇒ the "has N usages" popup can never appear), LSP4IJ's usage support per server, and
 * finally invokes `ShowUsagesAction` to fire a real `textDocument/references` request.
 */
class PhpDiagnosticsActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!System.getProperty(DIAG_PROPERTY, "false").toBoolean()) return
        val log = Logger.getInstance("php-diag")
        delay(5_000) // let IDE startup settle; the LSP support check below retries
        val setup = withContext(Dispatchers.EDT) {
            runCatching { openAndPlaceCaret(project, log) }
                .onFailure { log.warn("php-diag: setup failed", it) }
                .getOrNull()
        } ?: return
        val (editor, psiFile, offset) = setup

        for (attempt in 1..8) {
            val leaf = psiFile.findElementAt(offset)
            val supported = LSPFindUsagesHandlerFactory.isUsageSupportedByLanguageServer(leaf)
            log.info(
                "php-diag: isUsageSupportedByLanguageServer=$supported leaf=${leaf?.javaClass?.simpleName}" +
                    " leafLanguage=${leaf?.language?.id} (attempt $attempt)"
            )
            if (supported) break
            delay(3_000)
        }

        withContext(Dispatchers.EDT) {
            runCatching {
                log.info(
                    "php-diag: dumb=${com.intellij.openapi.project.DumbService.getInstance(project).isDumb}" +
                        " isIndexingAll=${ProjectIndexingManager.isIndexingAll()}" +
                        " indexingGate=${ProjectIndexingManager.canExecuteLSPFeature(psiFile)}" +
                        " findUsagesProviders=" +
                        LanguageFindUsages.INSTANCE.allForLanguage(psiFile.language).joinToString { it.javaClass.name }
                )
                // Call our evaluator directly: separates "evaluator logic" from "EP consulted".
                val leaf = psiFile.findElementAt(offset)
                val direct = leaf?.let { PhpTargetElementEvaluator().getNamedElement(it) }
                log.info(
                    "php-diag: directEvaluatorCall=${direct?.javaClass?.name ?: "NULL"}" +
                        (direct as? com.redhat.devtools.lsp4ij.features.LSPPsiElement)?.let { " range=${it.textRange}" } ?: ""
                )
                val target = GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)
                log.info("php-diag: findElementToShowUsagesOf=${target?.javaClass?.name ?: "NULL"}")
                if (target != null) {
                    ShowUsagesAction.startFindUsages(
                        target,
                        JBPopupFactory.getInstance().guessBestPopupLocation(editor),
                        editor,
                    )
                    log.info("php-diag: ShowUsagesAction.startFindUsages invoked")
                }
            }.onFailure { log.warn("php-diag: check failed", it) }
        }
    }

    private fun openAndPlaceCaret(project: Project, log: Logger): Triple<com.intellij.openapi.editor.Editor, com.intellij.psi.PsiFile, Int> {
        val basePath = project.basePath ?: error("project has no basePath")
        val vf = LocalFileSystem.getInstance().findFileByPath("$basePath/main.php")
            ?: error("main.php not found under $basePath")
        FileEditorManager.getInstance(project).openFile(vf, true)
        val fe = FileEditorManager.getInstance(project).getSelectedEditor(vf) as? TextEditor
            ?: error("no text editor for main.php")
        val editor = fe.editor
        val doc = editor.document
        val declLine = (0 until doc.lineCount).firstOrNull { i ->
            doc.getText(TextRange(doc.getLineStartOffset(i), doc.getLineEndOffset(i))).contains("function credentialWhere")
        } ?: error("credentialWhere declaration not found in main.php")
        val lineText = doc.getText(TextRange(doc.getLineStartOffset(declLine), doc.getLineEndOffset(declLine)))
        val offset = doc.getLineStartOffset(declLine) + lineText.indexOf("credentialWhere") + 2
        editor.caretModel.moveToOffset(offset)

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: error("no PSI for main.php")
        log.info(
            "php-diag: psiFile=${psiFile.javaClass.simpleName} language=${psiFile.language.id}" +
                " offset=$offset isFileSupported=${LanguageServersRegistry.getInstance().isFileSupported(psiFile)}" +
                " wordRange=${LSPIJUtils.getWordRangeAt(doc, psiFile, offset)}"
        )
        return Triple(editor, psiFile, offset)
    }

    companion object {
        const val DIAG_PROPERTY = "php.portable.diagnose"
    }
}
