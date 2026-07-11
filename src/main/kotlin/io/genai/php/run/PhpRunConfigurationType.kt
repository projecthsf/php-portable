package io.genai.php.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullLazyValue

class PhpRunConfigurationType : ConfigurationTypeBase(
    "PhpPortableRunConfiguration",
    // Deliberately NOT plain "PHP Script": on PhpStorm / IDEA Ultimate the official PHP plugin
    // already contributes a "PHP Script" run type, and two identical names are indistinguishable
    // in the Run menu. The "(Portable)" suffix makes it obvious which is ours.
    "PHP Script (Portable)",
    "Run a PHP script with a portable PHP interpreter",
    NotNullLazyValue.createValue { AllIcons.Actions.Execute },
) {
    init {
        addFactory(PhpConfigurationFactory(this))
    }
}
