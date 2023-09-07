package me.rerere.unocssintellij.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import me.rerere.unocssintellij.UnocssBundle
import me.rerere.unocssintellij.settings.UnocssSettingsState.ColorPreviewType
import me.rerere.unocssintellij.settings.UnocssSettingsState.MatchType

class UnocssSettingsComponent {

    val panel: DialogPanel
    private val settings = UnocssSettingsState.instance
    private lateinit var enableCheckbox: Cell<JBCheckBox>
    private lateinit var previewRemToPxCheckbox: Cell<JBCheckBox>

    init {
        panel = panel {
            row {
                enableCheckbox = checkBox(UnocssBundle.message("setting.enable")).bindSelected(settings::enable)
            }

            group(UnocssBundle.message("setting.documentation.title")) {
                row {
                    previewRemToPxCheckbox =
                        checkBox(UnocssBundle.message("setting.documentation.rem_to_px.checkbox.title")).bindSelected(
                            settings::remToPxPreview
                        ).comment(UnocssBundle.message("setting.documentation.rem_to_px.checkbox.comment"))
                }
                row(UnocssBundle.message("setting.documentation.rem_to_px.ratio.title")) {
                    spinner(0.0..100.0, 1.0).bindValue(settings::remToPxRatio).validationOnInput {
                        val num = it.value.toString().toDoubleOrNull()
                        if (num == null) error("Invalid number") else null
                    }.errorOnApply("Invalid radio number") { it.value.toString().toDoubleOrNull() == null }
                }.comment(UnocssBundle.message("setting.documentation.rem_to_px.ratio.comment"))
                    .enabledIf(previewRemToPxCheckbox.selected)
                buttonsGroup {
                    row(UnocssBundle.message("setting.documentation.color_preview.title")) {
                        radioButton(
                            UnocssBundle.message("setting.documentation.color_preview.option.none"),
                            ColorPreviewType.NONE
                        )
                        radioButton(
                            UnocssBundle.message("setting.documentation.color_preview.option.line_marker"),
                            ColorPreviewType.LINE_MARKER
                        )
                        radioButton(
                            UnocssBundle.message("setting.documentation.color_preview.option.inlay_hint"),
                            ColorPreviewType.INLAY_HINT
                        )
                    }
                }.bind(settings::colorPreviewType)
            }.visibleIf(enableCheckbox.selected)

            group(UnocssBundle.message("setting.autocomplete.title")) {
                buttonsGroup {
                    row(UnocssBundle.message("setting.autocomplete.match_type.title")) {
                        radioButton(
                            UnocssBundle.message("setting.autocomplete.match_type.option.prefix"),
                            MatchType.PREFIX
                        )
                        radioButton(
                            UnocssBundle.message("setting.autocomplete.match_type.option.fuzzy"),
                            MatchType.FUZZY
                        )
                    }
                }.bind(settings::matchType)

                row(UnocssBundle.message("setting.autocomplete.max_items.title")) {
                    intTextField(1..1000, 1).bindIntText(settings::maxItems)
                        .comment(UnocssBundle.message("setting.autocomplete.max_items.comment")).validationOnInput {
                            val num = it.text.toIntOrNull()
                            if (num == null) error("Invalid number") else null
                        }.errorOnApply("Invalid number") { it.text.toIntOrNull() == null }
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