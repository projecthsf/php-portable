package io.genai.php.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.Project

class PhpConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "PhpPortableScript"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        PhpRunConfiguration(project, this, "PHP")

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        PhpRunConfigurationOptions::class.java
}
