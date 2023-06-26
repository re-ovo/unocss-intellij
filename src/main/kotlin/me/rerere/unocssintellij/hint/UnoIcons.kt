package me.rerere.unocssintellij.hint

import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.parser.SVGLoader
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.net.URLDecoder
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.math.roundToInt

private fun svgDataUrlToSvgElement(url: String): String {
    val decoded = URLDecoder.decode(url, "UTF-8")
    val index = decoded.indexOf("<svg")
    return decoded.substring(index)
}

private val loader = SVGLoader()

class SVGIcon(encodedUrl: String): Icon {
    private val svg = svgDataUrlToSvgElement(encodedUrl)
    private val image = loader.load(svg.byteInputStream()) ?: error("Failed to load svg")

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        image.render(c as JComponent?, g as Graphics2D, ViewBox(x.toFloat(), y.toFloat(), 24f, 24f))
    }

    override fun getIconWidth(): Int {
        return 24
    }

    override fun getIconHeight(): Int {
        return 24
    }
}

