package me.rerere.unocssintellij.listener

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import me.rerere.unocssintellij.UnocssService

class FileListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        val service = project.service<UnocssService>()

        // Try load config when file opened, not after language injected
        // So that we can get a usable service instance before language injected
        service.onFileOpened(file)
    }
}