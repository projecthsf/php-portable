package io.genai.php.lang

/** Single source of truth for what counts as a PHP file, by extension. */
object PhpFiles {
    /** Extensions we treat as PHP. Used both to bind our file type (Community-only,
     *  see [PhpLanguageActivation]) and by the tooling, which keys off the extension
     *  directly so it keeps working even where another plugin owns the PHP file type. */
    val EXTENSIONS: Set<String> = setOf("php", "phtml", "php5", "php7", "php8", "inc")
}
