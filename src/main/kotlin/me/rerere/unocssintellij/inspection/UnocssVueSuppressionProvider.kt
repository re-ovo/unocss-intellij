package me.rerere.unocssintellij.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import me.rerere.unocssintellij.settings.UnocssSettingsState

class UnocssVueSuppressionProvider : HighlightInfoFilter {
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (!UnocssSettingsState.instance.enable) return true
        if (highlightInfo.severity == HighlightSeverity.WARNING) {
            if (highlightInfo.description.contains("Unknown at rule @apply")) {
                println("Suppressed warning: ${highlightInfo}")
               // return false
            }
        }
        return true
    }
}