package me.rerere.unocssintellij.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*

class UnocssSettingsComponent {

    val panel: DialogPanel
    private val settings = UnocssSettingsState.instance
    private lateinit var enableCheckbox: Cell<JBCheckBox>
    private lateinit var previewRemToPxCheckbox: Cell<JBCheckBox>

    init {
        panel = panel {
            row {
                enableCheckbox = checkBox("Enable unocss").bindSelected(settings::enable)
            }

            group("Documentation") {
                row {
                    previewRemToPxCheckbox = checkBox("Rem to px Preview")
                        .bindSelected(settings::remToPxPreview)
                        .comment("Enable/disable rem to px preview in hover document")
                }
                row("Rem To Px Ratio") {
                    spinner(0.0..100.0, 1.0)
                        .bindValue(settings::remToPxRatio)
                        .comment("Radio of rem to px, used in rem to px preview")
                        .validationOnInput {
                            val num = it.value.toString().toDoubleOrNull()
                            if (num == null) error("Invalid number") else null
                        }
                        .errorOnApply("Invalid radio number") { it.value.toString().toDoubleOrNull() == null }
                }.enabledIf(previewRemToPxCheckbox.selected)
            }.visibleIf(enableCheckbox.selected)

            group("Autocomplete") {
                buttonsGroup {
                    row("Match Type") {
                        radioButton("Prefix", "prefix")
                        radioButton("Fuzzy", "fuzzy")
                    }
                }.bind(settings::matchType)

                row("Max Items") {
                    intTextField(1..1000, 1)
                        .bindIntText(settings::maxItems)
                        .comment("The maximum number of items to show in autocomplete")
                        .validationOnInput {
                            val num = it.text.toIntOrNull()
                            if (num == null) error("Invalid number") else null
                        }
                        .errorOnApply("Invalid number") { it.text.toIntOrNull() == null }
                }
            }.visibleIf(enableCheckbox.selected)
        }
    }

    val isModified get() = panel.isModified()

    fun reset() {
        panel.reset()
    }

    fun apply() {
        panel.apply()
    }

    override fun toString(): String {
        return settings.toString()
    }
}