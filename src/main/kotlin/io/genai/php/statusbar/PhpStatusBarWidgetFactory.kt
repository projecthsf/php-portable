package io.genai.php.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class PhpStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = PhpStatusBarWidget.WIDGET_ID
    override fun getDisplayName(): String = "PHP Interpreter"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = PhpStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
}
