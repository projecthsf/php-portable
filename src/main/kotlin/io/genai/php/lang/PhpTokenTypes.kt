package io.genai.php.lang

import com.intellij.psi.tree.IElementType

object PhpTokenTypes {
    @JvmField val HTML = IElementType("PHP_HTML", PhpLanguage)
    @JvmField val PHP_OPEN_TAG = IElementType("PHP_OPEN_TAG", PhpLanguage)
    @JvmField val PHP_CLOSE_TAG = IElementType("PHP_CLOSE_TAG", PhpLanguage)
    @JvmField val KEYWORD = IElementType("PHP_KEYWORD", PhpLanguage)
    @JvmField val IDENTIFIER = IElementType("PHP_IDENTIFIER", PhpLanguage)
    @JvmField val VARIABLE = IElementType("PHP_VARIABLE", PhpLanguage)
    @JvmField val STRING = IElementType("PHP_STRING", PhpLanguage)
    @JvmField val NUMBER = IElementType("PHP_NUMBER", PhpLanguage)
    @JvmField val LINE_COMMENT = IElementType("PHP_LINE_COMMENT", PhpLanguage)
    @JvmField val BLOCK_COMMENT = IElementType("PHP_BLOCK_COMMENT", PhpLanguage)
    @JvmField val OPERATOR = IElementType("PHP_OPERATOR", PhpLanguage)

    /** Case-insensitive keyword set (PHP keywords are not case-sensitive). */
    val KEYWORDS: Set<String> = setOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch",
        "class", "clone", "const", "continue", "declare", "default", "do", "echo",
        "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
        "endswitch", "endwhile", "enum", "extends", "false", "final", "finally",
        "fn", "for", "foreach", "function", "global", "goto", "if", "implements",
        "include", "include_once", "instanceof", "insteadof", "interface", "isset",
        "list", "match", "namespace", "new", "null", "or", "print", "private",
        "protected", "public", "readonly", "require", "require_once", "return",
        "static", "switch", "throw", "trait", "true", "try", "unset", "use", "var",
        "while", "xor", "yield",
    )
}
