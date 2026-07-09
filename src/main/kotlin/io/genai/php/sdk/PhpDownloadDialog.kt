package io.genai.php.sdk

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/** Lets the user pick which portable PHP build to download. */
class PhpDownloadDialog(releases: List<PhpRelease>) : DialogWrapper(true) {
    private val combo = ComboBox(releases.toTypedArray())

    var selected: PhpRelease? = null
        private set

    init {
        title = "Download Portable PHP"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("PHP version:") {
            cell(combo).align(AlignX.FILL)
        }
        row {
            comment("Downloaded to ~/.php-portable and registered as a PHP SDK.")
        }
    }

    override fun doOKAction() {
        selected = combo.selectedItem as? PhpRelease
        super.doOKAction()
    }
}
