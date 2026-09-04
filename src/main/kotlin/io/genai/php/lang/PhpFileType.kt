package io.genai.php.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * `.php` file type. Referenced by plugin.xml via fieldName="INSTANCE" — a Kotlin
 * `object` already exposes a static `INSTANCE` field, so no extra declaration needed.
 */
object PhpFileType : LanguageFileType(PhpLanguage) {

    // The New UI PHP file icon (expui/language/php.svg from the platform, with its _dark
    // variant picked up automatically by IconLoader), so .php files look the same as in
    // current PhpStorm.
    private val ICON: Icon = IconLoader.getIcon("/icons/php.svg", PhpFileType::class.java.classLoader)

    // Anchor PhpLanguage and PhpTokenTypes (and their IElementTypes) to the MAIN plugin
    // descriptor. FileTypeManager instantiates this class at startup, long before any dynamic
    // plugin churn. Without the anchor, the first class-init of PhpTokenTypes can happen inside
    // an lsp.xml extension notification (e.g. while LSP4IJ is being dynamically loaded or
    // unloaded); the platform then attributes our language and element types to that
    // sub-descriptor, so toggling LSP4IJ tombstones them and every .php editor throws
    // "Trying to access element type from unloaded plugin: tombstone of PHP_…" (SEVERE in
    // idea.log, highlighting dies until restart). Touching a PhpTokenTypes member here forces
    // the <clinit> while the main descriptor owns the class.
    @Suppress("unused")
    private val TOKEN_TYPES_ANCHOR: Set<String> = PhpTokenTypes.KEYWORDS

    override fun getName(): String = "PHP File"
    override fun getDescription(): String = "PHP source file"
    override fun getDefaultExtension(): String = "php"
    override fun getIcon(): Icon? = ICON
}
