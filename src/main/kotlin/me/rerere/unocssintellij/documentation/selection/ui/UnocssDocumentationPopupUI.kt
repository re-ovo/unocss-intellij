package me.rerere.unocssintellij.documentation.selection.ui

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScreenUtil
import com.intellij.ui.WidthBasedLayout
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel


private class UnocssDocumentationPopupPane(
    presentationPane: JPanel,
    private val variableHeightComponent: JComponent,
) : JPanel(BorderLayout()), WidthBasedLayout {

    init {
        border = JBUI.Borders.empty()
        add(presentationPane, BorderLayout.NORTH)
        add(variableHeightComponent, BorderLayout.CENTER)
    }

    override fun getPreferredWidth(): Int = preferredSize.width

    override fun getPreferredHeight(width: Int): Int {
        variableHeightComponent.putClientProperty(FORCED_WIDTH, width)
        try {
            return preferredSize.height
        } finally {
            variableHeightComponent.putClientProperty(FORCED_WIDTH, null)
        }
    }
}

class UnocssDocumentationPopupUI(
    val project: Project,
    ui: UnocssDocumentationUI
) : Disposable {
    private var _ui: UnocssDocumentationUI? = ui

    val ui: UnocssDocumentationUI get() = requireNotNull(_ui) { "already detached" }
    val component: JComponent

    val preferableFocusComponent: JComponent get() = ui.editorPane

    private val coroutineScope: CoroutineScope = CoroutineScope(Job())

    private lateinit var myPopup: AbstractPopup

    init {
        val editorPane = ui.editorPane

        val gearActions = DefaultActionGroup()
        gearActions.add(OpenInToolWindowAction())

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, gearActions, true)
            .also {
                it.setSecondaryActionsIcon(AllIcons.Actions.More, true)
                it.targetComponent = editorPane
                it.isReservePlaceAutoPopupIcon = false
            }.component.apply { border = JBUI.Borders.empty() }

        val presentationPane = JPanel(BorderLayout()).also {
            it.border = JBUI.Borders.emptyRight(12)
            it.add(ui.presentationPane, BorderLayout.WEST)
            it.add(actionToolbar, BorderLayout.EAST)
        }

        component = UnocssDocumentationPopupPane(presentationPane, ui.containerPane)
    }

    fun setPopup(popup: AbstractPopup) {
        Disposer.register(popup, this)
        myPopup = popup

        coroutineScope.launch(Dispatchers.EDT) {
            ui.contentUpdates.collectLatest {
                resizePopup(popup)
                yield()
            }
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
        _ui?.let {
            Disposer.dispose(it)
            _ui = null
        }
    }

    private fun detachUI(): UnocssDocumentationUI {
        EDT.assertIsEdt()
        val ui = ui
        _ui = null
        return ui
    }

    private inner class OpenInToolWindowAction : AnAction(
        "Open in Tool Window",
        "Moves the content of the current popup into a preview tab of tool window",
        AllIcons.Actions.MoveToRightBottom,
    ), ActionToIgnore {

        override fun actionPerformed(e: AnActionEvent) {
            val documentationUI = detachUI()
            myPopup.cancel()
            UnocssDocumentationToolWindowManager.instance(project)
                .showInToolWindow(documentationUI)
        }
    }
}

private fun resizePopup(popup: AbstractPopup) {
    val location = UIUtil.getLocationOnScreen(popup.component)
    if (location == null) {
        popup.size = popup.component.preferredSize.adjustForEvent(popup)
        return
    }
    // Ensure that the popup can fit the screen if placed in the top left corner.
    val bounds = Rectangle(ScreenUtil.getScreenRectangle(location).location, popup.component.preferredSize)
    ScreenUtil.cropRectangleToFitTheScreen(bounds)
    // Don't resize to an empty popup
    if (bounds.size.width > 50 && bounds.size.height > 20) {
        popup.size = bounds.size.adjustForEvent(popup)
    }
}

internal fun Dimension.adjustForEvent(popup: AbstractPopup): Dimension {
    // when navigating, allow only for making the control wider
    val curSize = popup.size ?: return this
    if (curSize.width > width) {
        return Dimension(curSize.width, WidthBasedLayout.getPreferredHeight(popup.component, curSize.width))
    }
    return this
}
