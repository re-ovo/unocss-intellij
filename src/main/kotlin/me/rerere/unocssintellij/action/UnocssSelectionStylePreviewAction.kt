package me.rerere.unocssintellij.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PopupAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.application
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.documentation.selection.resolveSelectionStyle


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
        val project = editor.project ?: CommonDataKeys.PROJECT.getData(e.dataContext) ?: return

        val caretModel = editor.caretModel
        val selectRanges = if (caretModel.caretCount == 1) {
            arrayOf(TextRange(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd))
        } else {
            caretModel.allCarets.map { TextRange(it.selectionStart, it.selectionEnd) }.toTypedArray()
        }

        project.service<UnocssService>().scope.launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
            val selectionStyle = resolveSelectionStyle(project, editor.document, selectRanges)
            doPreview(project, editor, selectionStyle)
        }
    }

    protected abstract fun doPreview(project: Project, editor: Editor, selectionStyle: String?)
}
