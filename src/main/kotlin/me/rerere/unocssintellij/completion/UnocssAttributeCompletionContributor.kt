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
import com.intellij.psi.PsiFile
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElementType
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.isClassAttribute
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons
import me.rerere.unocssintellij.util.trimCss

class UnocssAttributeCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN),
            UnocssAttributeCompletionProvider
        )
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

        var unocssPrefix = ""
        val completionPrefix = extractTypingPrefix(result.prefixMatcher.prefix)
        val prefix = if (element.elementType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            val xmlAttributeEle = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
                ?: return
            val xmlName = xmlAttributeEle.firstChild

            if (isClassAttribute(xmlName.text)) {
                completionPrefix
            } else {
                unocssPrefix = xmlName.text
                "${xmlName.text}-$completionPrefix"
            }
        } else {
            completionPrefix
        }

        val project = element.project
        val service = project.service<UnocssService>()

        ApplicationUtil.runWithCheckCanceled({
            val maxItems = UnocssSettingsState.instance.maxItems
            service.getCompletion(parameters.originalFile.virtualFile, prefix, maxItems = maxItems)
        }, ProgressManager.getInstance().progressIndicator).forEach { suggestion ->
            val className = if (unocssPrefix.isNotBlank()) {
                suggestion.className.substring(unocssPrefix.length + 1)
            } else {
                suggestion.className
            }

            val colors = parseColors(suggestion.css)
            val icon = parseIcons(suggestion.css)
            result.addElement(
                LookupElementBuilder
                    .create(className)
                    .withPresentableText(suggestion.className)
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

    private fun extractTypingPrefix(prefix: String) = prefix.trim().split(" ").last()
}

// Make auto popup work when typing '-'
// https://github.com/JetBrains/intellij-community/blob/4d5322af326084873e16cfafd0239a1713a52adc/plugins/terminal/src/org/jetbrains/plugins/terminal/exp/TerminalCompletionAutoPopupHandler.kt#L19
class TypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!project.service<UnocssService>().isProcessRunning()) return Result.CONTINUE

        val values = file.childrenOfType<HtmlTag>()
        if (values.isEmpty()) return Result.CONTINUE

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