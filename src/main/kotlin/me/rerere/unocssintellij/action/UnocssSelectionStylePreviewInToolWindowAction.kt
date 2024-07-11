package me.rerere.unocssintellij.action

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import me.rerere.unocssintellij.documentation.selection.ui.SelectionResult
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationToolWindowManager
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationUI

class UnocssSelectionStylePreviewInToolWindowAction : UnocssSelectionStylePreviewAction() {

    override fun doPreview(project: Project, editor: Editor, selectionResult: SelectionResult) {
        UnocssDocumentationToolWindowManager.instance(project)
            .showInToolWindow(UnocssDocumentationUI(project,selectionResult))
    }
}