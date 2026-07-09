package io.genai.php.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import io.genai.php.sdk.PhpSdkType
import io.genai.php.settings.PhpInterpreterSettings
import java.io.File

class PhpRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?,
) : RunConfigurationBase<PhpRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): PhpRunConfigurationOptions =
        super.getOptions() as PhpRunConfigurationOptions

    var scriptPath: String?
        get() = options.scriptPath
        set(value) { options.scriptPath = value }

    var sdkName: String?
        get() = options.sdkName
        set(value) { options.sdkName = value }

    var interpreterPath: String?
        get() = options.interpreterPath
        set(value) { options.interpreterPath = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        PhpSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            @Throws(ExecutionException::class)
            override fun startProcess(): ProcessHandler {
                val script = scriptPath?.takeIf { it.isNotBlank() }
                    ?: throw ExecutionException("No PHP script specified")
                val php = resolveInterpreter()
                    ?: throw ExecutionException(
                        "No PHP interpreter configured — pick a PHP SDK or set an executable path",
                    )

                val cmd = GeneralCommandLine()
                cmd.exePath = php
                cmd.addParameter(script)
                File(script).parentFile?.let { cmd.setWorkDirectory(it) }

                val handler = OSProcessHandler(cmd)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }

    /**
     * An explicit interpreter path wins; then the SDK pinned on this config; otherwise
     * the current default interpreter (the one shown in the status bar).
     */
    private fun resolveInterpreter(): String? {
        interpreterPath?.takeIf { it.isNotBlank() }?.let { return it }

        // A pinned SDK wins — but only if it still exists on disk.
        val name = sdkName?.takeIf { it.isNotBlank() }
        val pinned = name?.let { ProjectJdkTable.getInstance().findJdk(it) }
        pinned?.homePath?.let { PhpSdkType.findPhpExecutable(it) }?.let { return it.absolutePath }

        // Otherwise (unpinned, or pinned install was deleted) use the current default.
        val default = PhpInterpreterSettings.getInstance().defaultSdk()
        return default?.homePath?.let { PhpSdkType.findPhpExecutable(it)?.absolutePath }
    }
}
