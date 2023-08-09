package me.rerere.unocssintellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "me.rerere.unocssintellij.settings.UnocssSettingsState",
    storages = [Storage("UnocssIntellij.xml")]
)
class UnocssSettingsState : PersistentStateComponent<UnocssSettingsState> {

    companion object {
        val instance: UnocssSettingsState
            get() = ApplicationManager.getApplication().service<UnocssSettingsState>()
    }

    var enable = true
    // var colorPreview = true
    var matchType = "prefix"
    var maxItems = 50
    var remToPxPreview = true
    var remToPxRatio = 16.0

    override fun getState(): UnocssSettingsState = this

    override fun loadState(state: UnocssSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun toString(): String {
        return "UnocssSettingsState(enable=$enable, matchType='$matchType', maxItems=$maxItems, remToPxPreview=$remToPxPreview, remToPxRatio=$remToPxRatio)"
    }
}