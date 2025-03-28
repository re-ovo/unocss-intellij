@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.marker.inlay

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.lang.Language
import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.css.CssFunction
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.xml.XmlToken
import com.intellij.ui.ColorChooserService
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.ColorIcon
import kotlinx.coroutines.*
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.marker.inlay.UnocssColorPreviewInlayHintsProviderFactory.Meta
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.settings.UnocssSettingsState.ColorAndIconPreviewType.INLAY_HINT
import me.rerere.unocssintellij.util.*
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class UnocssColorPreviewInlayHintsProviderFactory : InlayHintsProviderFactory {

    object Meta {
        val supportedLanguages = setOf(
            XMLLanguage.INSTANCE,
            CSSLanguage.INSTANCE,
            JavascriptLanguage,
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

object UnocssColorPreviewInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val name: String = "Unocss Color Preview"
    override val key: SettingsKey<NoSettings> = Meta.settingsKey
    override val previewText: String = "<div text-red class=\"bg-blue-400\"></div>"
    override val description: String = "Unocss color preview on token"

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }

    override fun createSettings(): NoSettings = NoSettings()

    override val isVisibleInSettings = false

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        val project = file.project
        if (DumbService.isDumb(project) || project.isDefault) {
            return null
        }
        val settingsState = UnocssSettingsState.of(project)
        if (settingsState.colorAndIconPreviewType != INLAY_HINT) {
            return null
        }
        val virtualFile = file.virtualFile
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) {
            return null
        }

        val fileContent = PsiDocumentManager.getInstance(project).getDocument(file)?.text ?: ""
        if (StringUtil.isEmptyOrSpaces(fileContent)) {
            return null
        }

        val unocssService = project.service<UnocssService>()
        val resolveResult = runBlockingCancellable {
            withTimeoutOrNull(800) {
                unocssService.resolveAnnotations(virtualFile, fileContent)
            }
        } ?: return null

        return UnocssPreviewCollector(editor, getMatchedPositions(fileContent, resolveResult))
    }
}

private class UnocssPreviewCollector(editor: Editor, private val matchedPositions: List<MatchedPosition>) :
    FactoryInlayHintsCollector(editor) {

    private val scaleAwarePresentationFactory = ScaleAwarePresentationFactory(editor, factory)

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (!element.isUnocssCandidate() && element !is CssFunction) {
            return true
        }

        collectElementsByMatchedPositions(element, sink, editor)

        collectElementsByThemeConfig(element, sink, editor)

        return true
    }

    private fun collectElementsByMatchedPositions(element: PsiElement, sink: InlayHintsSink, editor: Editor) {
        matchedPositions
            .filter { it.start >= element.startOffset && it.end <= element.endOffset }
            .mapNotNull {
                when (element.elementType) {
                    XmlTokenType.XML_NAME -> {
                        // make sure it has no attr value
                        if (element.nextSibling == null) {
                            UnocssResolveMeta(element, it.text, null) to it.start
                        } else null
                    }

                    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN -> {
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
                        wrapWithColorDialog(
                            buildColorPresentation(colorValue.first(), editor),
                            colorValue.first(),
                            editor,
                            resolveMeta,
                            startOffset
                        ),
                        false
                    )
                }

                val iconValue = parseIcons(css)
                if (iconValue != null) {
                    buildIconPresentation(iconValue, editor)?.let {
                        sink.addInlineElement(startOffset, true, it, false)
                    }
                }
            }
    }

    private fun collectElementsByThemeConfig(element: PsiElement, sink: InlayHintsSink, editor: Editor) {
        if (element.inCssThemeFunction()) {
            element.parentOfType<CssFunction>()
                ?.takeIf { it.name == "theme" }
                ?.let { themeFunc ->
                    val colorValue = UnocssConfigManager.getThemeValue(element.text.trim('\'', '"')) ?: return
                    val jbColor = parseHexColor(colorValue) ?: return

                    val presentation = buildColorPresentation(jbColor, editor)
                    sink.addInlineElement(themeFunc.startOffset, false, presentation, false)
                }
        }
    }

    private fun buildColorPresentation(
        color: JBColor,
        editor: Editor,
    ): InlayPresentation {
        val padding = InlayPresentationFactory.Padding(2, 2, 2, 2)
        val bgColor = editor.colorsScheme
            .getColor(DefaultLanguageHighlighterColors.INLINE_REFACTORING_SETTINGS_DEFAULT)
        val roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6)

        val scaleFactory = scaleAwarePresentationFactory
        val base = scaleFactory.lineCentered(
            scaleFactory.container(
                scaleFactory.colorIcon(color),
                padding,
                roundedCorners,
                bgColor
            )
        )

        val inset = scaleFactory.inset(factory.text(""), 0, 4, 0, 0)
        return factory.seq(base, inset)
    }

    private fun wrapWithColorDialog(
        base: InlayPresentation,
        color: JBColor,
        editor: Editor,
        meta: UnocssResolveMeta,
        startOffset: Int
    ): InlayPresentation {
        val clickListener: (MouseEvent, Point) -> Unit = { event: MouseEvent, _: Point ->
            val point = RelativePoint(event.component, event.point)

            val colorListener = UnocssTokenUpdateColorListener(editor, startOffset, meta.copy())
            ColorChooserService.instance.showPopup(
                project = editor.project!!,
                currentColor = color,
                listener = colorListener,
                location = point,
                showAlpha = true,
                popupCloseListener = { colorListener.dispose() }
            )
        }

        return factory.onClick(
            factory.withCursorOnHover(
                base,
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            ),
            MouseButton.Left,
            clickListener
        )
    }

    private fun buildIconPresentation(icon: String, editor: Editor): InlayPresentation? {
        val svgIcon = SVGIcon.tryGetIcon(icon, 18).getOrNull() ?: return null
        val padding = InlayPresentationFactory.Padding(2, 2, 2, 2)
        val bgColor = editor.colorsScheme
            .getColor(DefaultLanguageHighlighterColors.INLINE_REFACTORING_SETTINGS_DEFAULT)
        val roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6)

        val scaleFactory = scaleAwarePresentationFactory
        val base = scaleFactory.lineCentered(
            scaleFactory.container(
                scaleFactory.icon(svgIcon),
                padding,
                roundedCorners,
                bgColor
            )
        )
        val inset = scaleFactory.inset(factory.text(""), 0, 4, 0, 0)
        return factory.seq(base, inset)
    }
}

private class UnocssTokenUpdateColorListener(
    private val editor: Editor,
    private val matchPositionStartOffset: Int,
    private val meta: UnocssResolveMeta
) : ColorListener, Disposable {

    private var bindElement: PsiElement = meta.bindElement

    private val elementText: String = bindElement.text

    private val cs = CoroutineScope(Dispatchers.EDT)

    private var job: Job? = null

    override fun colorChanged(color: Color, source: Any?) {
        job?.cancel()
        job = cs.launch {
            doColorChanged(color)
        }
    }

    private suspend fun doColorChanged(color: Color) {
        when {
            // xml attribute value token
            bindElement.elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN -> {
                val oldValue = meta.attrValue ?: return
                val newValue = generateNewValue(editor, oldValue, color, matchPositionStartOffset)
                    ?: return

                tryReplaceBindElementWithText(newValue.restorePattern())
            }

            // JS String Leaf
            bindElement.isLeafJsLiteral() || bindElement.isLeafJsString() -> {
                val oldValue = meta.attrName
                val newValue = generateNewValue(editor, oldValue, color, matchPositionStartOffset) ?: return

                tryReplaceBindElementWithText(newValue.restorePattern())
            }

            // 整个元素就是一个属性
            bindElement.isLeafXmlName() -> {
                val oldValue = elementText
                val newValue = generateNewValue(editor, oldValue, color, matchPositionStartOffset) ?: return

                if (!tryReplaceBindElementWithText(newValue)) {
                    HintManager.getInstance().showErrorHint(
                        editor,
                        "Cannot update automatically, please replace it manually: $newValue",
                        matchPositionStartOffset,
                        matchPositionStartOffset,
                        HintManager.ABOVE,
                        HintManager.HIDE_BY_ANY_KEY,
                        5000
                    )
                }
            }

            else -> {
                println(bindElement.javaClass.name)
                HintManager.getInstance().showErrorHint(
                    editor,
                    "Unsupported element, please replace it manually: ${color.toHex(true)}",
                    matchPositionStartOffset,
                    matchPositionStartOffset,
                    HintManager.ABOVE,
                    HintManager.HIDE_BY_ANY_KEY,
                    5000
                )
            }
        }
    }

    fun generateNewValue(editor: Editor, oldVal: String, color: Color, startOffset: Int) =
        when {
            oldVal.startsWith("bg-") -> "bg-[${color.toHex(true)}]"
            oldVal.startsWith("text-") -> "text-[${color.toHex(true)}]"
            oldVal.startsWith("border-") -> "border-[${color.toHex(true)}]"
            else -> {
                // Unknown unocss color pattern
                HintManager.getInstance().showErrorHint(
                    editor,
                    "Could not generate, please replace it manually: ${color.toHex(true)}",
                    startOffset,
                    startOffset,
                    HintManager.ABOVE,
                    HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_OTHER_HINT,
                    5000
                )
                null
            }
        }

    private suspend fun tryReplaceBindElementWithText(newText: String): Boolean {
        val ele = bindElement
        return if (ele is LeafPsiElement) {
            writeAction {
                executeCommand {
                    bindElement = ele.replaceWithText(newText) as PsiElement
                }
            }
            true
        } else false
    }

    private fun String.restorePattern(): String {
        val startOffsetRelative = matchPositionStartOffset - bindElement.startOffset
        val initElementText: String = meta.bindElement.text

        val stopIndex = initElementText.lastIndexOfAny(charArrayOf(' ', '"', '\'', '\n'))

        return initElementText.replaceRange(startOffsetRelative, stopIndex, this)
    }

    override fun dispose() {
        cs.cancel("UnocssTokenUpdateColorListener disposed!")
    }
}

private fun PsiElement.isLeafXmlName() =
    this is LeafPsiElement && this is XmlToken && this.elementType.toString() == "XML_NAME"

private fun PsiElement.isLeafJsString() =
    this is LeafPsiElement && this.elementType.toString().startsWith("JS:STRING")

private fun ScaleAwarePresentationFactory.colorIcon(color: JBColor, size: Int = 18, arc: Int = 4) =
    smallScaledIcon(ColorIcon(size, size, size, size, color, false, arc))
