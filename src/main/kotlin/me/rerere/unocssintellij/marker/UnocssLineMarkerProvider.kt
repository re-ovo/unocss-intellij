package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.settings.UnocssSettingsState.ColorPreviewType
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons

abstract class UnocssLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!UnocssSettingsState.instance.enable
            || UnocssSettingsState.instance.colorPreviewType != ColorPreviewType.LINE_MARKER
        ) {
            return null
        }
        return doGetLineMarkerInfo(element)
    }

    abstract fun doGetLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>?

    protected fun getLineMarkerInfo(meta: UnocssResolveMeta): LineMarkerInfo<*>? {
        val css = meta.resolveCss()?.css ?: return null

        // color icon
        val colorValue = parseColors(css)
        if (colorValue.isNotEmpty()) {
            return LineMarkerInfo(
                meta.bindElement,
                meta.bindElement.textRange,
                ColorIcon(12, colorValue.first()),
                null,
                null,
                GutterIconRenderer.Alignment.LEFT,
            ) { "UNOCSS" }
        }

        // svg icon
        val iconValue = parseIcons(css) ?: return null
        val img = SVGIcon.tryGetIcon(iconValue).getOrNull() ?: return null
        return LineMarkerInfo(
            meta.bindElement,
            meta.bindElement.textRange,
            img,
            null,
            null,
            GutterIconRenderer.Alignment.LEFT
        ) { "UNOCSS" }
    }
}