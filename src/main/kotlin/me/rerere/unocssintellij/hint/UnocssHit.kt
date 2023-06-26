@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.hint

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.JPanel

// TODO: implement this
// https://github.com/JetBrains/intellij-community/blob/idea/231.9161.38/plugins/kotlin/idea/src/org/jetbrains/kotlin/idea/codeInsight/hints/KotlinInlayParameterHintsProvider.kt
class UnocssHit : InlayHintsProvider<NoSettings> {
    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        println("[hint] file: $file $editor $sink")
        return null
    }

    // region useless part since we don't need to create a settings panel
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object: ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }

    override val key: SettingsKey<NoSettings>
        get() = SETTING_KEY
    override val name: String
        get() = "UNOCSS HINTS"
    override val previewText: String?
        get() = null

    override fun createSettings(): NoSettings = NoSettings()

    companion object {
        val SETTING_KEY = SettingsKey<NoSettings>("UNOCSSHints")
    }
    // endregion
}