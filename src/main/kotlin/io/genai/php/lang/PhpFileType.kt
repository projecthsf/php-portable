package io.genai.php.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * `.php` file type. Referenced by plugin.xml via fieldName="INSTANCE" — a Kotlin
 * `object` already exposes a static `INSTANCE` field, so no extra declaration needed.
 */
object PhpFileType : LanguageFileType(PhpLanguage) {
    private val ICON: Icon = IconLoader.getIcon("/icons/php.svg", PhpFileType::class.java)

    override fun getName(): String = "PHP File"
    override fun getDescription(): String = "PHP source file"
    override fun getDefaultExtension(): String = "php"
    override fun getIcon(): Icon = ICON
}
