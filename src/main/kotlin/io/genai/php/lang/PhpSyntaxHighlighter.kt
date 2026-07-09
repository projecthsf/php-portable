package io.genai.php.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey as key
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class PhpSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = PhpLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            PhpTokenTypes.PHP_OPEN_TAG, PhpTokenTypes.PHP_CLOSE_TAG -> pack(TAG)
            PhpTokenTypes.KEYWORD -> pack(KEYWORD)
            PhpTokenTypes.STRING -> pack(STRING)
            PhpTokenTypes.NUMBER -> pack(NUMBER)
            PhpTokenTypes.LINE_COMMENT -> pack(LINE_COMMENT)
            PhpTokenTypes.BLOCK_COMMENT -> pack(BLOCK_COMMENT)
            PhpTokenTypes.VARIABLE -> pack(VARIABLE)
            PhpTokenTypes.IDENTIFIER -> pack(IDENTIFIER)
            PhpTokenTypes.OPERATOR -> pack(OPERATOR)
            else -> EMPTY
        }

    companion object {
        val TAG: TextAttributesKey = key("PHP_TAG", Colors.MARKUP_TAG)
        val KEYWORD: TextAttributesKey = key("PHP_KEYWORD", Colors.KEYWORD)
        val STRING: TextAttributesKey = key("PHP_STRING", Colors.STRING)
        val NUMBER: TextAttributesKey = key("PHP_NUMBER", Colors.NUMBER)
        val LINE_COMMENT: TextAttributesKey = key("PHP_LINE_COMMENT", Colors.LINE_COMMENT)
        val BLOCK_COMMENT: TextAttributesKey = key("PHP_BLOCK_COMMENT", Colors.BLOCK_COMMENT)
        val VARIABLE: TextAttributesKey = key("PHP_VARIABLE", Colors.INSTANCE_FIELD)
        val IDENTIFIER: TextAttributesKey = key("PHP_IDENTIFIER", Colors.IDENTIFIER)
        val OPERATOR: TextAttributesKey = key("PHP_OPERATOR", Colors.OPERATION_SIGN)
        private val EMPTY = emptyArray<TextAttributesKey>()
    }
}
