package me.rerere.unocssintellij.documentation.selection.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.content.Content
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.unocssintellij.documentation.selection.lookupSelectionMatchedPositions
import me.rerere.unocssintellij.documentation.selection.mergeCss
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext

private val TW_UI_KEY: Key<UnocssDocumentationToolWindowUI> = Key.create("unocss.documentation.tw.ui")

class UnocssDocumentationToolWindowUI(
    val ui: UnocssDocumentationUI,
    private val content: Content
) : Disposable {

    val contentComponent = JPanel(BorderLayout()).also { panel ->
        panel.border = JBUI.Borders.empty()
        ui.presentationPane.toggleIconVisible(false)
        panel.add(ui.presentationPane, BorderLayout.NORTH)
        panel.add(ui.containerPane, BorderLayout.CENTER)
    }

    private var reusable: Disposable?

    val isReusable: Boolean
        get() {
            EDT.assertIsEdt()
            return reusable != null
        }

    private val cs = CoroutineScope(EmptyCoroutineContext)

    init {
        content.putUserData(TW_UI_KEY, this)
        Disposer.register(content, this)
        Disposer.register(this, ui)
        reusable = cs.updateContentTab(content).also {
            Disposer.register(this, it)
        }

        EditorFactory.getInstance().eventMulticaster.apply {
            addSelectionListener(MyEditorSelectionListener(), content.toolWindowUI)
        }
    }

    override fun dispose() {
        cs.cancel("UnocssDocumentationToolWindowUI disposal")
        content.putUserData(TW_UI_KEY, null)
    }

    private inner class MyEditorSelectionListener : SelectionListener {
        override fun selectionChanged(event: SelectionEvent) {
            if (event.newRange.isEmpty) {
                ui.updateContent(null)
                return
            }
            if (event.oldRanges.contentEquals(event.newRanges)) return
            val project = event.editor.project ?: return
            val psiFile = PsiDocumentManager.getInstance(project)
                .getPsiFile(event.editor.document) ?: return

            cs.launch(Dispatchers.EDT) {
                val matchedPositions = lookupSelectionMatchedPositions(psiFile, event.newRanges)

                if (ui.hasContent) {
                    val oldMatchedPositions = lookupSelectionMatchedPositions(psiFile, event.oldRanges)
                    if (!oldMatchedPositions.contentEquals(matchedPositions)) {
                        val selectionStyle = mergeCss(matchedPositions, project)
                        ui.updateContent(selectionStyle)
                    }
                } else {
                    val selectionStyle = mergeCss(matchedPositions, project)
                    ui.updateContent(selectionStyle)
                }
            }
        }
    }
}

internal val Content.toolWindowUI: UnocssDocumentationToolWindowUI get() = checkNotNull(getUserData(TW_UI_KEY))

internal val Content.isReusable: Boolean get() = toolWindowUI.isReusable

private fun CoroutineScope.updateContentTab(content: Content): Disposable {
    val updateJob = launch(Dispatchers.EDT) {
        content.displayName = "Selection Style"
    }
    return Disposable(updateJob::cancel)
}

private infix fun <T> List<T>?.contentEquals(other: List<T>?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this === other) {
        return true
    }

    val length = this.size
    if (other.size != length) {
        return false
    }

    for (i in 0 ..< length) {
        if (this[i] != other[i]) {
            return false
        }
    }

    return true
}