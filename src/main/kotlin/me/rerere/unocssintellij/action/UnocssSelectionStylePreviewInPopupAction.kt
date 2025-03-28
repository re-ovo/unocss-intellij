package me.rerere.unocssintellij.action

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JBColor
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.popup.AbstractPopup
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationPopupUI
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationUI

class UnocssSelectionStylePreviewInPopupAction : UnocssSelectionStylePreviewAction() {

    override fun doPreview(project: Project, editor: Editor, selectionStyle: String?) {
        val ui = UnocssDocumentationUI(project, selectionStyle)
        val popupUI = UnocssDocumentationPopupUI(project, ui)

        val popupBuilder = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(popupUI.component, popupUI.preferableFocusComponent)
            .setProject(project)
            .setShowBorder(false)
            .setResizable(true)
            .setMovable(true)
            .setFocusable(true)
            .setModalContext(false)

        if (WindowRoundedCornersManager.isAvailable() && SystemInfoRt.isMac && !JBColor.isBright()) {
            popupBuilder.setShowBorder(true)
        }

        val popup = popupBuilder.createPopup() as AbstractPopup
        popupUI.setPopup(popup)

        popup.showInBestPositionFor(editor)
    }
}
