package io.genai.php.lsp

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.features.LSPPsiElement

/**
 * Teaches the platform's target-element machinery what the "name" under the caret is for our
 * language, by returning an [LSPPsiElement] (LSP4IJ's fake element for an identifier range).
 *
 * Why we need our own: our PSI is flat (one leaf per lexer token, no `PsiReference`s), so
 * `TargetElementUtil` can't resolve a target the usual way. LSP4IJ ships an evaluator for
 * exactly this, but it registers it with `language=""` — and `LanguageExtensionPoint` uses the
 * raw `language` attribute as the lookup key while `LanguageExtension.forLanguage()` matches by
 * exact language ID — so that evaluator never runs, for any language (upstream LSP4IJ bug).
 *
 * Without a working evaluator, `TargetElementUtil.findTargetElement(editor, ELEMENT_FOR_CARET)`
 * returns null for `.php` files, which breaks:
 *  - Cmd+Click / Cmd+B on a method *declaration*: `GotoDeclarationAction` resolves it to the
 *    element itself and wants to fall back to `findElementToShowUsagesOf()` →
 *    `ShowUsagesAction` ("Method 'x' has N usages" popup) — that helper is
 *    `TargetElementUtil`-based and returned null, so no popup and no way to jump to callers;
 *  - other target-based consumers (e.g. Find Usages fallbacks).
 *
 * The hook that matters is `getNamedElement`: `findElementToShowUsagesOf` uses flags
 * `ELEMENT_FOR_CARET`, whose path ends in `TargetElementUtilBase.getNamedElement(element)` —
 * which consults this evaluator first, then falls back to `PsiNamedElement` parents (none in a
 * flat PSI). Registered via `<targetElementEvaluator language="PhpPortable">` in
 * META-INF/lsp.xml, so it only exists when LSP4IJ is installed and only applies to our language.
 */
class PhpTargetElementEvaluator : TargetElementEvaluatorEx2() {

    override fun getNamedElement(element: PsiElement): PsiElement? {
        val file: PsiFile = element.containingFile ?: return null
        val supported = LanguageServersRegistry.getInstance().isFileSupported(file)
        val isLeaf = element.firstChild == null
        val result = if (supported && isLeaf && element.textRange.length > 0) {
            LSPPsiElement(file, element.textRange)
        } else {
            null
        }
        val log = LOG
        if (log.isDebugEnabled) {
            log.debug(
                "php-diag: getNamedElement element=${element.javaClass.simpleName}" +
                    " text=${element.text.take(20)} supported=$supported" +
                    " result=${result?.javaClass?.simpleName ?: "NULL"}"
            )
        }
        return result
    }

    private companion object {
        val LOG: Logger = Logger.getInstance(PhpTargetElementEvaluator::class.java)
    }
}
