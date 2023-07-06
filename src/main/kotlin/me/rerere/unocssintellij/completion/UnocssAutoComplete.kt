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
import com.intellij.psi.util.childrenOfType
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.lang.psi.UnocssClassValue
import me.rerere.unocssintellij.lang.psi.UnocssTypes
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons
import me.rerere.unocssintellij.util.trimCss

class UnocssAutoComplete : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement(UnocssTypes.CLASSNAME),
            UnocssCompletionProvider()
        )
    }
}

class UnocssCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        val service = project.service<UnocssService>()

        val prefix = result.prefixMatcher.prefix
        val file = parameters.originalFile.virtualFile

        ApplicationUtil.runWithCheckCanceled({
            service.getCompletion(
                file,
                prefix,
                prefix.length
            )
        }, ProgressManager.getInstance().progressIndicator).forEach { suggestion ->
            val colors = parseColors(suggestion.css)
            val icon = parseIcons(suggestion.css)
            result.addElement(
                LookupElementBuilder
                    .create(suggestion.className)
                    .withTypeText("Unocss")
                    .withIcon(
                        if (colors.isNotEmpty()) {
                            ColorIcon(16, colors.first())
                        } else if(icon != null ){
                            SVGIcon(icon)
                        } else null
                    )
                    .withTailText(trimCss(suggestion.css), true)
            )
        }

        result.restartCompletionOnAnyPrefixChange()
    }
}

// Make auto popup work when typing '-'
// https://github.com/JetBrains/intellij-community/blob/4d5322af326084873e16cfafd0239a1713a52adc/plugins/terminal/src/org/jetbrains/plugins/terminal/exp/TerminalCompletionAutoPopupHandler.kt#L19
class TypedHandler: TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if(!project.service<UnocssService>().isProcessRunning()) return Result.CONTINUE

        val values = file.childrenOfType<UnocssClassValue>()
        if(values.isEmpty()) return Result.CONTINUE

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