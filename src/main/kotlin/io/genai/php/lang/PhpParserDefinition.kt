package io.genai.php.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * A minimal parser definition. It does NOT parse a PHP grammar — it builds a FLAT PSI tree
 * (one leaf per lexer token) so the file has real structure instead of a single whole-file
 * element. That word-level granularity is what the editor needs for navigation, selection,
 * etc.: e.g. Cmd-hover underlines the identifier token under the cursor rather than the whole
 * file. Semantic understanding still comes from the language server (Phpactor) via LSP; this
 * is purely the local PSI skeleton the platform expects a language to provide.
 */
class PhpParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = PhpLexer()

    override fun createParser(project: Project?): PsiParser = PhpParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = WHITESPACE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = PhpFile(viewProvider)

    /** Flat parse: consume every token as a direct leaf child of the file node. */
    private class PhpParser : PsiParser {
        override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
            val rootMarker = builder.mark()
            while (!builder.eof()) builder.advanceLexer()
            rootMarker.done(root)
            return builder.treeBuilt
        }
    }

    private class PhpFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, PhpLanguage) {
        override fun getFileType(): FileType = PhpFileType
        override fun toString(): String = "PHP File"
    }

    companion object {
        private val FILE = IFileElementType(PhpLanguage)
        private val WHITESPACE = TokenSet.create(TokenType.WHITE_SPACE)
        private val COMMENTS = TokenSet.create(PhpTokenTypes.LINE_COMMENT, PhpTokenTypes.BLOCK_COMMENT)
        private val STRINGS = TokenSet.create(PhpTokenTypes.STRING)
    }
}
