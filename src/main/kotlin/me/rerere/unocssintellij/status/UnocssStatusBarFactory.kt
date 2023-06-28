package me.rerere.unocssintellij.status

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import me.rerere.unocssintellij.UnocssService

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
                    object : AnAction("Update Config")  {
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
        return WidgetState(null, "Unocss", true)
    }
}