package me.rerere.unocssintellij.marker

import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.parser.SVGLoader
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import me.rerere.unocssintellij.util.toHex
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.net.URLDecoder
import javax.swing.Icon
import javax.swing.JComponent

private const val ICON_SIZE = 20

private fun svgDataUrlToSvgElement(url: String): String {
    val decoded = URLDecoder.decode(url, "UTF-8")
    val index = decoded.indexOf("<svg")

    val iconColor = JBUI.CurrentTheme.Label.foreground().toHex()
    return decoded.substring(index).replace("currentColor", iconColor)
}

private val loader = SVGLoader()

class SVGIcon(encodedUrl: String) : Icon {
    private val svg = svgDataUrlToSvgElement(encodedUrl)
    private val image = loader.load(svg.byteInputStream()) ?: error("Failed to load svg")

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        val iconSizeFloat = JBUIScale.scale(ICON_SIZE.toFloat())
        image.render(
            c as JComponent?,
            g as Graphics2D,
            ViewBox(x.toFloat(), y.toFloat(), iconSizeFloat, iconSizeFloat)
        )
    }

    override fun getIconWidth(): Int {
        return JBUIScale.scale(ICON_SIZE)
    }

    override fun getIconHeight(): Int {
        return JBUIScale.scale(ICON_SIZE)
    }

    companion object {
        @JvmStatic
        fun tryGetIcon(encodedUrl: String): Result<SVGIcon> = runCatching {
            SVGIcon(encodedUrl)
        }.onFailure {
            it.printStackTrace()
        }
    }
}
