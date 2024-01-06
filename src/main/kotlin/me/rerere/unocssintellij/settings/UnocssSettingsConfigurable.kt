package me.rerere.unocssintellij.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import me.rerere.unocssintellij.UnocssService
import javax.swing.JComponent

class UnocssSettingsConfigurable(private val project: Project) : Configurable {

    private var mySettingsComponent: UnocssSettingsComponent? = null

    override fun getDisplayName(): String {
        return "Unocss"
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = UnocssSettingsComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val component = mySettingsComponent ?: return false
        return component.isModified
    }

    override fun apply() {
        val component = mySettingsComponent ?: return
        val matchTypeBefore = UnocssSettingsState.instance.matchType
        component.apply()
        val matchTypeAfter = UnocssSettingsState.instance.matchType
        if (matchTypeBefore != matchTypeAfter) {
            project.service<UnocssService>().updateSettings()
        }
        DaemonCodeAnalyzer.getInstance(project).restart()
        UnocssSettingsState.updateJsLiteralMatchPatterns()
    }

    override fun reset() {
        val component = mySettingsComponent ?: return
        component.reset()
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}