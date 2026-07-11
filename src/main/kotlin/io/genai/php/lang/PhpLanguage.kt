package io.genai.php.lang

import com.intellij.lang.Language

/**
 * The PHP language for this plugin.
 *
 * The ID is deliberately **"PhpPortable"**, NOT "PHP": IntelliJ requires language IDs to
 * be globally unique, and the official JetBrains PHP plugin (com.jetbrains.php, bundled in
 * PhpStorm / installable in IDEA Ultimate) already registers ID "PHP". Sharing the ID would
 * clash the moment both are present. A distinct ID lets us coexist quietly; the display name
 * is still "PHP" so nothing looks off in the UI. The ID must match the `language=` attributes
 * in plugin.xml.
 */
object PhpLanguage : Language("PhpPortable") {
    override fun getDisplayName(): String = "PHP"
}
