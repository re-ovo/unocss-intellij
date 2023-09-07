package me.rerere.unocssintellij.marker.inlay

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.lang.Language
import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.JBColor
import com.intellij.util.ui.ColorIcon
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.marker.inlay.UnocssColorPreviewInlayHitsProviderFactory.Meta
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.settings.UnocssSettingsState.ColorPreviewType
import me.rerere.unocssintellij.util.*
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class UnocssColorPreviewInlayHitsProviderFactory : InlayHintsProviderFactory {

    object Meta {
        val supportedLanguages = setOf(
            HTMLLanguage.INSTANCE,
            CSSLanguage.INSTANCE,
            JavascriptLanguage.INSTANCE,
        )

        val settingsKey = SettingsKey<NoSettings>("unocss.colorPreview.hints")
    }

    override fun getProvidersInfo() = Meta.supportedLanguages.map {
        ProviderInfo(it, UnocssColorPreviewInlayHintsProvider)
    }

    override fun getLanguages(): Iterable<Language> {
        return Meta.supportedLanguages
    }

    override fun getProvidersInfoForLanguage(language: Language): List<InlayHintsProvider<out Any>> {
        return Meta.supportedLanguages.filter { language.isKindOf(it) }
            .map { UnocssColorPreviewInlayHintsProvider }
    }
}

@Suppress("UnstableApiUsage")
object UnocssColorPreviewInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val name: String = "Unocss Color Preview"
    override val key: SettingsKey<NoSettings> = Meta.settingsKey
    override val previewText: String?
    override val description: String?

    init {
        previewText = "<div text-red class=\"bg-blue-400\"></div>"
        description = "Unocss color preview on token"
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        val project = file.project
        return if (!DumbService.isDumb(project) && !project.isDefault) {
            val unocssService = project.service<UnocssService>()
            val virtualFile = file.virtualFile
            if (virtualFile == null || !virtualFile.isInLocalFileSystem) {
                return null
            }

            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
            val fileContent = document.text
            if (StringUtil.isEmptyOrSpaces(fileContent)) {
                return null
            }

            val resolveResult = runBlocking {
                withTimeoutOrNull(1000) {
                    unocssService.resolveAnnotations(file.virtualFile, fileContent)
                }
            } ?: return null

            MyCollector(editor, getMatchedPositions(fileContent, resolveResult))
        } else null
    }

    class MyCollector(editor: Editor, private val matchedPositions: List<MatchedPosition>) :
        FactoryInlayHintsCollector(editor) {

        private val scaleAwarePresentationFactory = ScaleAwarePresentationFactory(editor, factory)

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (!UnocssSettingsState.instance.enable
                || UnocssSettingsState.instance.colorPreviewType != ColorPreviewType.INLAY_HINT
            ) {
                return false
            }
            element.node ?: return false
            if (!element.isUnocssCandidate()) {
                return true
            }

            matchedPositions
                .filter { it.start >= element.startOffset && it.end <= element.endOffset }
                .mapNotNull {
                    when (element.elementType) {
                        XmlElementType.XML_NAME -> {
                            // make sure it has no attr value
                            if (element.nextSibling == null) {
                                UnocssResolveMeta(element, it.text, null) to it.start
                            } else null
                        }

                        XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN -> {
                            element.parentOfType<XmlAttributeValue>()?.let { attrValueEle ->
                                val xmlName = attrValueEle.parent.firstChild.text
                                val offset = element.startOffset
                                val attrValue = element.text.substring(it.start - offset, it.end - offset)

                                UnocssResolveMeta(element, xmlName, attrValue) to it.start
                            }
                        }

                        else -> UnocssResolveMeta(element, it.text, null) to it.start
                    }
                }
                .forEach { (resolveMeta, startOffset) ->
                    val css = resolveMeta.resolveCss()?.css ?: return@forEach

                    val colorValue = parseColors(css)
                    if (colorValue.isNotEmpty()) {
                        sink.addInlineElement(
                            startOffset,
                            true,
                            buildPresentation(colorValue.first(), editor),
                            false
                        )
                    }

                    val iconValue = parseIcons(css)
                    if(iconValue != null) {
                        sink.addInlineElement(
                            startOffset,
                            true,
                            buildPresentation(iconValue, editor),
                            false
                        )
                    }
                }

            return true
        }

        private fun buildPresentation(color: JBColor, editor: Editor): InlayPresentation {
            val padding = InlayPresentationFactory.Padding(3, 3, 2, 2)
            val bgColor = editor.colorsScheme
                .getColor(DefaultLanguageHighlighterColors.INLINE_REFACTORING_SETTINGS_DEFAULT)
            val roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6)

            val scaleFactory = scaleAwarePresentationFactory
            val base = scaleFactory.lineCentered(
                scaleFactory.container(
                    scaleFactory.smallScaledIcon(ColorIcon(16, color)),
                    padding,
                    roundedCorners,
                    bgColor
                )
            )
            val inset = scaleFactory.inset(factory.text(""), 0, 4, 0, 0)
            return factory.seq(base, inset)
        }

        private fun buildPresentation(icon: String, editor: Editor): InlayPresentation {
            val svgIcon = SVGIcon.tryGetIcon(icon,16).getOrNull() ?: return buildPresentation(JBColor(0x000000, 0xFFFFFF), editor)
            val padding = InlayPresentationFactory.Padding(3, 3, 2, 2)
            val bgColor = editor.colorsScheme
                .getColor(DefaultLanguageHighlighterColors.INLINE_REFACTORING_SETTINGS_DEFAULT)
            val roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6)

            val scaleFactory = scaleAwarePresentationFactory
            val base = scaleFactory.lineCentered(
                scaleFactory.container(
                    scaleFactory.smallScaledIcon(svgIcon),
                    padding,
                    roundedCorners,
                    bgColor
                )
            )
            val inset = scaleFactory.inset(factory.text(""), 0, 4, 0, 0)
            return factory.seq(base, inset)
        }
    }
}
