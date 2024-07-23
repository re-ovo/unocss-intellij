package me.rerere.unocssintellij.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.util.ui.JBEmptyBorder
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.settings.UnocssSettingsState.ColorAndIconPreviewType
import me.rerere.unocssintellij.settings.UnocssSettingsState.MatchType
import me.rerere.unocssintellij.util.RoundedBorder
import me.rerere.unocssintellij.util.UnocssBundle
import org.intellij.lang.regexp.RegExpLanguage
import java.awt.Dimension

class UnocssSettingsConfigurable(private val project: Project) : BoundSearchableConfigurable(
    displayName = "Unocss",
    helpTopic = ""
) {

    val settings = UnocssSettingsState.of(project)

    override fun createPanel(): DialogPanel = panel {
        lateinit var previewRemToPxCheckbox: Cell<JBCheckBox>

        group(UnocssBundle.message("setting.annotation.title")) {

            row {
                checkBox(UnocssBundle.message("setting.annotation.underline.title"))
                    .bindSelected(settings::enableUnderline)
                    .comment(UnocssBundle.message("setting.annotation.underline.comment"))
            }

            buttonsGroup {
                row(UnocssBundle.message("setting.annotation.color_preview.title")) {
                    radioButton(
                        UnocssBundle.message("setting.annotation.color_preview.option.none"),
                        ColorAndIconPreviewType.NONE
                    )
                    radioButton(
                        UnocssBundle.message("setting.annotation.color_preview.option.line_marker"),
                        ColorAndIconPreviewType.LINE_MARKER
                    )
                    radioButton(
                        UnocssBundle.message("setting.annotation.color_preview.option.inlay_hint"),
                        ColorAndIconPreviewType.INLAY_HINT
                    )
                }
            }.bind(settings::colorAndIconPreviewType)
        }

        group(UnocssBundle.message("setting.documentation.title")) {
            row {
                checkBox(UnocssBundle.message("setting.documentation.include_mdn_docs"))
                    .bindSelected(settings::includeMdnDocs)
            }

            row {
                previewRemToPxCheckbox =
                    checkBox(UnocssBundle.message("setting.documentation.rem_to_px.checkbox.title"))
                        .bindSelected(settings::remToPxPreview)
                        .comment(UnocssBundle.message("setting.documentation.rem_to_px.checkbox.comment"))
            }

            row(UnocssBundle.message("setting.documentation.rem_to_px.ratio.title")) {
                spinner(0.0..100.0, 1.0)
                    .bindValue(settings::remToPxRatio)
                    .enabledIf(previewRemToPxCheckbox.selected)
            }.comment(UnocssBundle.message("setting.documentation.rem_to_px.ratio.comment"))
        }

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
                intTextField(1..1000, 1)
                    .bindIntText(settings::maxItems)
                    .comment(UnocssBundle.message("setting.autocomplete.max_items.comment"))
                    .validationOnInput {
                        val num = it.text.toIntOrNull()
                        if (num == null) error("Invalid number") else null
                    }
                    .errorOnApply("Invalid number") { it.text.toIntOrNull() == null }
            }
        }

        group(UnocssBundle.message("setting.folding.title")) {
            row {
                checkBox(UnocssBundle.message("setting.folding.folding_default"))
                    .bindSelected(settings::codeDefaultFolding)
            }

            row(UnocssBundle.message("setting.folding.length")) {
                intTextField(
                    range = 0..100,
                    keyboardStep = 1
                ).bindIntText(settings::foldingCodeLength)
            }

            row(UnocssBundle.message("setting.folding.placeholder")) {
                textField().bindText(settings::foldingPlaceholder)
            }
        }

        group(UnocssBundle.message("setting.matcher.title")) {

            // As the `LanguageTextField` cannot be aligned by dsl directly, we use this as a workaround
            row {
                panel {
                    row {
                        label(UnocssBundle.message("setting.matcher.jsliteral"))
                    }.topGap(TopGap.SMALL)
                }.align(AlignY.TOP).gap(RightGap.SMALL)

                panel {
                    row {
                        cell(createRegexEditor())
                            .bind(
                                { it.text },
                                { input, value -> input.text = value },
                                settings::jsLiteralMatchRegexPatterns.toMutableProperty()
                            )
                    }
                }.align(AlignY.TOP)
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    private fun createRegexEditor(): LanguageTextField {
        return LanguageTextField(
            RegExpLanguage.INSTANCE,
            project,
            settings.jsLiteralMatchRegexPatterns,
            false
        ).apply {
            preferredSize = Dimension(maximumSize.width, 96)
            border = JBEmptyBorder(3, 5, 3, 5)

            addSettingsProvider { editor ->
                editor.contentComponent.border = JBEmptyBorder(3, 5, 3, 5)
                editor.setBorder(
                    RoundedBorder(
                        DarculaUIUtil.getOutlineColor(true, false),
                        DarculaUIUtil.COMPONENT_ARC.get(),
                    )
                )
                editor.setVerticalScrollbarVisible(true)
                ReadAction.run<Throwable> {
                    SpellCheckingEditorCustomizationProvider.getInstance()
                        .enabledCustomization?.customize(editor)
                }
            }
        }
    }

    override fun apply() {
        val matchTypeBefore = settings.matchType
        super.apply()
        val matchTypeAfter = settings.matchType
        if (matchTypeBefore != matchTypeAfter) {
            project.service<UnocssService>().updateSettings()
        }
        DaemonCodeAnalyzer.getInstance(project).restart()
        settings.updateJsLiteralMatchPatterns(settings.jsLiteralMatchRegexPatterns)
    }
}
