package me.rerere.unocssintellij.documentation.selection.ui

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.ColorUtil
import java.awt.Color
import javax.swing.UIManager

data class StyledCodeStyleDefinition(
    val backgroundColor: Color?,
    val foregroundColor: Color?,
    val borderColor: Color?,
    val borderWidth: Int?,
    val borderRadius: Int?,
)

@Suppress("UseJBColor")
object IntellijDocumentationUISupport {

    private val blockCodeStyling = ControlColorStyleBuilder(
        "Code.Block",
        defaultBorderColor = Color(0xEBECF0),
        defaultBorderRadius = 10,
        defaultBorderWidth = 1,
    )

    fun getStyledCodeProperties(
        colorScheme: EditorColorsScheme,
        editorPaneBackgroundColor: Color,
    ): StyledCodeStyleDefinition {
        val defaultBgColor = colorScheme.defaultBackground
        val blockCodeStyling = if (ColorUtil.getContrast(defaultBgColor, editorPaneBackgroundColor) < 1.1)
            blockCodeStyling.copy(
                suffix = ".EditorPane",
                defaultBackgroundColor = Color(0x5A5D6B),
                defaultBackgroundOpacity = 4,
            )
        else blockCodeStyling

        return blockCodeStyling.getStyleDefinition(editorPaneBackgroundColor, colorScheme)
    }

    private data class ControlColorStyleBuilder(
        val id: String,
        val suffix: String = "",
        val defaultBackgroundColor: Color? = null,
        val defaultBackgroundOpacity: Int = 100,
        val defaultForegroundColor: Color? = null,
        val defaultBorderColor: Color? = null,
        val defaultBorderWidth: Int = 0,
        val defaultBorderRadius: Int = 0,
    ) {
        private val backgroundColor: Color? get() = UIManager.getColor("$id$suffix.backgroundColor")

        private val foregroundColor: Color? get() = UIManager.getColor("$id.foregroundColor")

        private val borderColor: Color? get() = UIManager.getColor("$id$suffix.borderColor")

        private val backgroundOpacity: Int? get() = UIManager.get("$id$suffix.backgroundOpacity") as? Int

        private val borderWidth: Int? get() = UIManager.get("$id.borderWidth") as? Int

        private val borderRadius: Int? get() = UIManager.get("$id.borderRadius") as? Int

        fun getStyleDefinition(
            editorPaneBackgroundColor: Color,
            editorColorsScheme: EditorColorsScheme
        ): StyledCodeStyleDefinition {

            val backgroundColor = choose(
                backgroundColor,
                defaultBackgroundColor,
                editorColorsScheme.defaultBackground
            )?.let {
                val opacity = choose(backgroundOpacity, defaultBackgroundOpacity) ?: 100
                mixColors(editorPaneBackgroundColor, it, opacity)
            }

            val color = choose(foregroundColor, defaultForegroundColor, editorColorsScheme.defaultForeground)
            val borderColor = choose(borderColor, defaultBorderColor)
            val borderWidth = choose(borderWidth, defaultBorderWidth)
            val borderRadius = choose(borderRadius, defaultBorderRadius)

            return StyledCodeStyleDefinition(backgroundColor, color, borderColor, borderWidth, borderRadius)
        }

        fun <T : Any> choose(themeVersion: T?, defaultVersion: T?, editorVersion: T? = null): T? =
            themeVersion ?: defaultVersion ?: editorVersion

        fun mixColors(c1: Color, c2: Color, opacity2: Int): Color {
            if (opacity2 >= 100) return c2
            if (opacity2 <= 0) return c1
            return Color(
                ((100 - opacity2) * c1.red + opacity2 * c2.red) / 100,
                ((100 - opacity2) * c1.green + opacity2 * c2.green) / 100,
                ((100 - opacity2) * c1.blue + opacity2 * c2.blue) / 100
            )
        }
    }
}

