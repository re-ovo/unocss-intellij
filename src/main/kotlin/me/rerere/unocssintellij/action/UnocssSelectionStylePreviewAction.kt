package me.rerere.unocssintellij.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PopupAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.application
import me.rerere.unocssintellij.documentation.selection.getSelectionRange
import me.rerere.unocssintellij.documentation.selection.parseSelectionForUnocss
import me.rerere.unocssintellij.documentation.selection.ui.SelectionResult


abstract class UnocssSelectionStylePreviewAction : AnAction(), PopupAction, DumbAware {

    private val AnActionEvent.editor: Editor? get() = CommonDataKeys.EDITOR.getData(dataContext)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val active = e.editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = active
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (application.isHeadlessEnvironment) {
            return
        }

        val editor = e.editor ?: return
        editor.getSelectionRange()
            ?.takeUnless { it.isEmpty }
            ?.let { onActionPerformed(e, editor, it) }
    }

    private fun onActionPerformed(e: AnActionEvent, editor: Editor, range: TextRange) {
        val project = editor.project ?: CommonDataKeys.PROJECT.getData(e.dataContext) ?: return

        val code = editor.parseSelectionForUnocss(range)
        doPreview(project, editor, SelectionResult(editor.document, code, range))
    }

    protected abstract fun doPreview(project: Project, editor: Editor, selectionResult: SelectionResult)
}
