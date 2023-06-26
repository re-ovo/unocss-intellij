package me.rerere.unocssintellij.util

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.vfs.VirtualFile

fun VirtualFile.toLocalVirtualFile(): VirtualFile {
    return if (this is VirtualFileWindow) {
        this.delegate
    } else {
        this
    }
}