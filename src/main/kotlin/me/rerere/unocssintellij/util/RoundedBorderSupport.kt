package me.rerere.unocssintellij.util

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.border.Border
import javax.swing.plaf.UIResource
import kotlin.math.max

internal class RoundedBorder(
    val borderColor: Color?,
    val borderRadius: Int,
    private val insets: JBInsets = JBUI.insets(2)
) : Border, UIResource {

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        if (g !is Graphics2D) {
            return
        }

        val r = Rectangle(x, y, width, height)

        // Fill outside part
        val arc = borderRadius.toFloat()
        val shape = Area(r)
        shape.subtract(
            Area(
                RoundRectangle2D.Float(
                    r.x + 0.5f, r.y + 0.5f, r.width - 1f, r.height - 1f, arc, arc
                )
            )
        )
        g.color = c.parent?.background ?: c.background
        g.fill(shape)

        // Paint border
        paintComponentBorder(g, r, borderRadius, borderColor, c.hasFocus())
    }

    override fun getBorderInsets(c: Component?) = insets

    override fun isBorderOpaque() = false
}

// ========== ported from com.intellij.ide.ui.laf.darcula.DarculaNewUIUtils

internal fun paintComponentBorder(
    g: Graphics,
    rect: Rectangle,
    borderRadius: Int,
    borderColor: Color?,
    focused: Boolean,
) {
    val g2 = g.create() as Graphics2D

    try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(
            RenderingHints.KEY_STROKE_CONTROL,
            if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE
        )

        g2.color = if (focused) JBUI.CurrentTheme.Focus.focusColor() else borderColor
        paintRectangle(g2, rect, borderRadius)
    } finally {
        g2.dispose()
    }
}

internal fun paintRectangle(g: Graphics2D, rect: Rectangle, arc: Int, thick: Int = 1) {
    val w = thick.toFloat()
    val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
    border.append(
        RoundRectangle2D.Float(
            0f,
            0f,
            rect.width.toFloat(),
            rect.height.toFloat(),
            arc.toFloat(),
            arc.toFloat()
        ), false
    )
    val innerArc = max(arc.toFloat() - thick * 2, 0.0f)
    border.append(RoundRectangle2D.Float(w, w, rect.width - w * 2, rect.height - w * 2, innerArc, innerArc), false)

    g.translate(rect.x, rect.y)
    g.fill(border)
}