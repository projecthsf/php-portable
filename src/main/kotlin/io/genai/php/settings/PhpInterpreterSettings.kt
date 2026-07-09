package io.genai.php.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import io.genai.php.sdk.PhpSdkManager
import io.genai.php.sdk.PhpSdkType

/**
 * Remembers which PHP interpreter is the "current" one. Application-level, since the
 * interpreters (SDKs) are application-level. Run configs with no explicit interpreter
 * fall back to this, so switching it in the status bar changes what runs.
 */
@Service(Service.Level.APP)
@State(name = "PhpPortable", storages = [Storage("php-portable.xml")])
class PhpInterpreterSettings : PersistentStateComponent<PhpInterpreterSettings.State> {

    class State {
        var defaultSdkName: String? = null
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    var defaultSdkName: String?
        get() = myState.defaultSdkName
        set(value) {
            myState.defaultSdkName = value
        }

    /**
     * The selected interpreter, restricted to ones that still exist on disk, falling
     * back to the first usable install (an interpreter's folder may have been deleted).
     */
    fun defaultSdk(): Sdk? {
        val usable = PhpSdkManager.listSdks().filter { PhpSdkType.findPhpExecutable(it.homePath) != null }
        return usable.firstOrNull { it.name == myState.defaultSdkName } ?: usable.firstOrNull()
    }

    companion object {
        fun getInstance(): PhpInterpreterSettings = service()
    }
}
