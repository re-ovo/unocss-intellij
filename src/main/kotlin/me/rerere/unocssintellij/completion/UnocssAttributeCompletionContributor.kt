package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElementType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.isClassAttribute
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons
import me.rerere.unocssintellij.util.trimCss

private val matchVariantGroupPrefixRE = Regex("^(.*\\s)?(.*)\\(([^)]*)$")

class UnocssAttributeCompletionContributor : CompletionContributor() {
    init {
        // match attribute value
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN),
            UnocssAttributeCompletionProvider
        )
        // match attribute name when using attributify presets
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(XmlElementType.XML_NAME),
            UnocssAttributeCompletionProvider
        )
    }
}

object UnocssAttributeCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        if (!UnocssSettingsState.instance.enable) return
        val element = parameters.position

        val xmlAttrName = if (element.elementType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            val xmlAttributeEle = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
                ?: return
            xmlAttributeEle.firstChild.text
        } else ""

        // Intellij CSS plugin seems to intervene class value prefix match,
        // so we may use the substring of element.text to keep the behavior of
        // class value and other attribute values consistent
        val prefixToResolve = element.text.substring(0, parameters.offset - element.startOffset)
        val (typingPrefix, prefixToSuggest) = resolvePrefix(element, prefixToResolve, xmlAttrName) ?: return

        val project = element.project
        val service = project.service<UnocssService>()

        val resultSet = result.withPrefixMatcher(typingPrefix)
        ApplicationUtil.runWithCheckCanceled({
            val maxItems = UnocssSettingsState.instance.maxItems
            service.getCompletion(parameters.originalFile.virtualFile, prefixToSuggest, maxItems = maxItems)
        }, ProgressManager.getInstance().progressIndicator).forEach { suggestion ->
            val className = if (typingPrefix != prefixToSuggest) {
                suggestion.className.substring(prefixToSuggest.length - typingPrefix.length)
            } else {
                suggestion.className
            }

            val colors = parseColors(suggestion.css)
            val icon = parseIcons(suggestion.css)
            resultSet.addElement(
                LookupElementBuilder
                    .create(className)
                    .withLookupString(suggestion.className)
                    .withPresentableText(className)
                    .withTypeText("Unocss")
                    .withIcon(
                        if (colors.isNotEmpty()) {
                            ColorIcon(16, colors.first())
                        } else if (icon != null) {
                            SVGIcon(icon)
                        } else null
                    )
                    .withTailText(trimCss(suggestion.css), true)
            )
        }

        result.restartCompletionOnAnyPrefixChange()
    }

    private fun resolvePrefix(element: PsiElement, prefix: String, attrName: String): PrefixHolder? {
        if (element.elementType != XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return PrefixHolder(prefix, prefix)
        }

        // attributify value
        if (!isClassAttribute(attrName)) {
            // use last().trim() to help manually trigger code completion (e.g. via keymap)
            // the attribute name will be used as prefix to query suggestions
            val typingPrefix = prefix.split(" ").last().trim()
            val removeVariantPrefix = typingPrefix.substring(typingPrefix.lastIndexOf(":") + 1)
            val prefixToSuggest = "$attrName-$removeVariantPrefix"
            return PrefixHolder(removeVariantPrefix, prefixToSuggest)
        }

        // class value

        val lastLeftBracket = prefix.lastIndexOf('(')
        val lastRightBracket = prefix.lastIndexOf(')')

        val typingPrefix: String
        val prefixToSuggest: String
        // no variant group or no need to complete variant group
        if (lastLeftBracket < 0 || lastLeftBracket < lastRightBracket) {
            typingPrefix = prefix.split(" ").last().trim()
            prefixToSuggest = typingPrefix

            // ignore completion when typing prefix is blank
            if (typingPrefix.isBlank()) {
                return null
            }
        } else {
            val matchResult = matchVariantGroupPrefixRE.find(prefix) ?: return null
            val variants = matchResult.groupValues[2]
            // use last().trim() to help manually trigger code completion (e.g. via keymap)
            // the variant group will be used as prefix to query suggestions
            val token = matchResult.groupValues[3].split(" ").last().trim()

            typingPrefix = token
            prefixToSuggest = "${variants.substring(variants.lastIndexOf(":") + 1)}$token"
        }

        return PrefixHolder(typingPrefix, prefixToSuggest)
    }

    private data class PrefixHolder(
        /**
         * typing prefix is the prefix that user is typing, used to let intellij to match with suggestion
         */
        val typingPrefix: String,
        /**
         * prefix to suggest is the prefix that will be used to query suggestions
         * usually has the same value as typing prefix, but
         *
         * - when using variant group, it will be added with variant prefix
         * - when using attributify presets, it will be added with html attribute name as prefix
         *
         * It will be fine to ignore variant prefix like `hover:` when querying suggestions
         */
        val prefixToSuggest: String
    )
}

// Make auto popup work when typing '-'
// https://github.com/JetBrains/intellij-community/blob/4d5322af326084873e16cfafd0239a1713a52adc/plugins/terminal/src/org/jetbrains/plugins/terminal/exp/TerminalCompletionAutoPopupHandler.kt#L19
class TypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!project.service<UnocssService>().isProcessRunning()) return Result.CONTINUE

        val phase = CompletionServiceImpl.getCompletionPhase()
        val lookup = LookupManager.getActiveLookup(editor)
        if (lookup is LookupImpl) {
            if (editor.selectionModel.hasSelection()) {
                lookup.performGuardedChange { EditorModificationUtil.deleteSelectedText(editor) }
            }
            return Result.STOP
        }

        if (Character.isLetterOrDigit(charTyped) || charTyped == '-') {
            if (phase is CompletionPhase.EmptyAutoPopup && phase.allowsSkippingNewAutoPopup(editor, charTyped)) {
                return Result.CONTINUE
            }
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            return Result.STOP
        }

        return Result.CONTINUE
    }
}