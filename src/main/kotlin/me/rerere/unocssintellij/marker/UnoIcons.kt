package me.rerere.unocssintellij.marker

import com.github.weisj.jsvg.SVGDocument
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

class SVGIcon(
    private val image: SVGDocument,
    private val size: Int = ICON_SIZE
) : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        val iconSizeFloat = JBUIScale.scale(size.toFloat())
        image.render(
            c as JComponent?,
            g as Graphics2D,
            ViewBox(x.toFloat(), y.toFloat(), iconSizeFloat, iconSizeFloat)
        )
    }

    override fun getIconWidth(): Int {
        return JBUIScale.scale(size)
    }

    override fun getIconHeight(): Int {
        return JBUIScale.scale(size)
    }

    companion object {
        @JvmStatic
        fun tryGetIcon(encodedUrl: String, size: Int = ICON_SIZE): Result<SVGIcon> = runCatching {
            val svg = svgDataUrlToSvgElement(encodedUrl)
            val image = loader.load(svg.byteInputStream()) ?: error("Failed to load svg")
            SVGIcon(image)
        }.onFailure {
            it.printStackTrace()
        }

        @JvmStatic
        fun fromStream(stream: java.io.InputStream, size: Int = ICON_SIZE): SVGIcon {
            val image = loader.load(stream) ?: error("Failed to load svg")
            return SVGIcon(image, size)
        }
    }
}
