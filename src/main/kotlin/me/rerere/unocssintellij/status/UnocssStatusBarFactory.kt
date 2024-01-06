package me.rerere.unocssintellij.status

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.util.UnocssBundle
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.settings.UnocssSettingsState

class UnocssStatusBarFactory : StatusBarWidgetFactory {
    companion object {
        const val ID = "UnocssStatusBar"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = "Unocss Status Bar"

    override fun isAvailable(project: Project): Boolean {
        return project.service<UnocssService>().isProcessRunning()
    }

    override fun createWidget(project: Project): StatusBarWidget = UnocssStatusPop(project)
}

class UnocssStatusPop(project: Project) : EditorBasedStatusBarPopup(project, false), CustomStatusBarWidget {
    override fun ID(): String {
        return UnocssStatusBarFactory.ID
    }

    override fun createInstance(project: Project): StatusBarWidget {
        return UnocssStatusPop(project)
    }

    override fun createPopup(context: DataContext): ListPopup {
        val group = object : ActionGroup() {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return arrayOf(
                    object : AnAction(UnocssBundle.message("status.action.updateConfig")) {
                        override fun actionPerformed(e: AnActionEvent) {
                            project.service<UnocssService>().updateConfigIfRunning()
                        }
                    }
                )
            }
        }
        return JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true
            )
    }

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        if (!UnocssSettingsState.instance.enable) return WidgetState.HIDDEN

        val text: String = if (file == null) {
            "Unocss"
        } else {
            val filename = file.name
            if (filename.endsWith(".js") || filename.endsWith(".ts")) {
                "Unocss"
            } else {
                val matched = runBlocking {
                    val content = file.contentsToByteArray().decodeToString()
                    withTimeoutOrNull(500) {
                        project.service<UnocssService>()
                            .resolveCss(file, content)
                            ?.matchedTokens ?: emptyList()
                    }
                } ?: emptyList()

                if (matched.isNotEmpty()) "Unocss: ${matched.size}" else "Unocss"
            }
        }

        return WidgetState(null, text, true)
    }
}