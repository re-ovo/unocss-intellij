package me.rerere.unocssintellij.documentation.selection.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.content.Content
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.unocssintellij.documentation.selection.getSelectionRange
import me.rerere.unocssintellij.documentation.selection.parseSelectionForUnocss
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
            addEditorMouseListener(EditorMouseDragListener(), content.toolWindowUI)
        }
    }

    override fun dispose() {
        cs.cancel("UnocssDocumentationToolWindowUI disposal")
        content.putUserData(TW_UI_KEY, null)
    }

    private inner class EditorMouseDragListener : EditorMouseListener {
        override fun mouseReleased(event: EditorMouseEvent) {
            cs.launch(start = CoroutineStart.UNDISPATCHED) {
                // Waiting for editor selection state update...
                delay(80)
                val selectionRange = readAction {
                    event.editor.getSelectionRange()
                }
                if (selectionRange == null) {
                    ui.updateContent(null)
                } else {
                    val selection = withContext(Dispatchers.IO) {
                        readAction {
                            event.editor.parseSelectionForUnocss(selectionRange)
                        }
                    }
                    ui.updateContent(SelectionResult(event.editor.document, selection, selectionRange))
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
