package me.rerere.unocssintellij.documentation.selection.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.unocssintellij.util.IconResources
import java.util.function.Supplier
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class UnocssDocumentationToolWindowManager(
    project: Project,
    private val cs: CoroutineScope
) {

    companion object {
        fun instance(project: Project): UnocssDocumentationToolWindowManager = project.service()
    }

    private val toolWindow = ToolWindowManager.getInstance(project)
        .registerToolWindow("unocss.documentation.toolwindow") {
            anchor = ToolWindowAnchor.RIGHT
            icon = IconResources.PluginIcon
            sideTool = true
            stripeTitle = Supplier { "UnoCSS" }
            shouldBeAvailable = false
        }

    private var waitForFocusRequest: Boolean = false

    init {
        toolWindow.installWatcher(toolWindow.contentManager)
    }

    fun showInToolWindow(ui: UnocssDocumentationUI) {
        EDT.assertIsEdt()

        val reusableContent = getReusableContent()
        if (reusableContent == null) {
            showInNewTab(ui)
        } else {
            Disposer.dispose(reusableContent.toolWindowUI)
            initUI(ui, reusableContent)
            makeVisible(reusableContent)
        }
    }

    private fun showInNewTab(ui: UnocssDocumentationUI) {
        val content = addNewContent()
        initUI(ui, content)
        makeVisible(content)
    }

    private fun initUI(ui: UnocssDocumentationUI, content: Content) {
        val windowUI = UnocssDocumentationToolWindowUI(ui, content)
        content.component = windowUI.contentComponent
    }

    private fun makeVisible(content: Content) {
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.show()
        waitForFocusRequest()
    }

    private fun getReusableContent(): Content? {
        return toolWindow.contentManager.contents.firstOrNull {
            it.isReusable
        }
    }

    private fun addNewContent(): Content {
        val content = ContentFactory.getInstance()
            .createContent(JPanel(), null, false)
            .also {
                it.isCloseable = true
                it.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
            }
        toolWindow.contentManager.addContent(content)
        return content
    }

    private fun waitForFocusRequest() {
        EDT.assertIsEdt()
        waitForFocusRequest = true
        cs.launch(Dispatchers.EDT) {
            delay(Registry.intValue("documentation.v2.tw.focus.invocation.timeout").toLong())
            waitForFocusRequest = false
        }
    }
}
