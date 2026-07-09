package io.genai.php.run

import com.intellij.execution.configurations.RunConfigurationOptions

/** Persisted state for a PHP run configuration. */
class PhpRunConfigurationOptions : RunConfigurationOptions() {
    private val scriptPathProp = string("").provideDelegate(this, "scriptPath")
    private val sdkNameProp = string("").provideDelegate(this, "sdkName")
    private val interpreterPathProp = string("").provideDelegate(this, "interpreterPath")

    var scriptPath: String?
        get() = scriptPathProp.getValue(this)
        set(value) = scriptPathProp.setValue(this, value)

    var sdkName: String?
        get() = sdkNameProp.getValue(this)
        set(value) = sdkNameProp.setValue(this, value)

    var interpreterPath: String?
        get() = interpreterPathProp.getValue(this)
        set(value) = interpreterPathProp.setValue(this, value)
}
