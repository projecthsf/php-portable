package io.genai.php.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullLazyValue

class PhpRunConfigurationType : ConfigurationTypeBase(
    "PhpPortableRunConfiguration",
    "PHP Script",
    "Run a PHP script with a portable PHP interpreter",
    NotNullLazyValue.createValue { AllIcons.Actions.Execute },
) {
    init {
        addFactory(PhpConfigurationFactory(this))
    }
}
