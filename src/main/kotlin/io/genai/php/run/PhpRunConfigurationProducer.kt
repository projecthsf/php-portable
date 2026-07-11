package io.genai.php.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import io.genai.php.lang.PhpFiles

/**
 * Makes a `.php` file runnable directly: right-click ▸ Run, and a gutter ▶ marker.
 * Auto-fills the script path and, if none is chosen yet, the first available PHP SDK.
 */
class PhpRunConfigurationProducer : LazyRunConfigurationProducer<PhpRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(PhpRunConfigurationType::class.java)
            .configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: PhpRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = phpFile(context) ?: return false
        configuration.scriptPath = file.path
        configuration.name = file.name
        // Leave the interpreter unpinned so the run follows the current default
        // (the status-bar selection); users can still pin one in the config editor.
        return true
    }

    override fun isConfigurationFromContext(
        configuration: PhpRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = phpFile(context) ?: return false
        val script = configuration.scriptPath ?: return false
        return FileUtil.pathsEqual(script, file.path)
    }

    private fun phpFile(context: ConfigurationContext): VirtualFile? {
        val vf = CommonDataKeys.VIRTUAL_FILE.getData(context.dataContext)
            ?: context.psiLocation?.containingFile?.virtualFile
        if (vf == null || vf.isDirectory) return null
        return if (vf.extension?.lowercase() in PhpFiles.EXTENSIONS) vf else null
    }
}
