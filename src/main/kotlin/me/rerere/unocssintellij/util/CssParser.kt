package me.rerere.unocssintellij.util

import com.intellij.lang.css.CSSLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.css.impl.CssLazyStylesheet
import com.intellij.psi.util.childrenOfType

import com.intellij.psi.util.elementType
import com.intellij.ui.JBColor
import java.awt.Color

private val CSS_RGBA_COLOR_PATTERN =
    """rgba\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*(?:[\d.]+|var\(--[a-zA-Z0-9-_]+\))\s*\)""".toRegex()

private val CSS_COMMENT_PATTERN = """/\*.*?\*/""".toRegex()
private val CSS_CONTENT_PATTERN = """(?<=\{)[^}]*(?=})""".toRegex()

private val CSS_UNO_ICON_PATTERN = """--un-icon:\s?url\(.+\);""".toRegex()

private val CSS_HEX_COLOR_SHORT_RE = "^#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])?$".toRegex()
private val CSS_HEX_COLOR_RE = "^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})?$".toRegex()

// parse colors from css
// pattern: rgba(255, 255, 255, var(--opacity))
fun parseColors(css: String): Set<JBColor> {
    val colors = mutableSetOf<JBColor>()
    CSS_RGBA_COLOR_PATTERN.findAll(css).forEach { matchResult ->
        val value = matchResult.value.removePrefix("rgba(").removeSuffix(")")
        val parts = value.split(",")
        val (r, g, b) = parts.map { it.trim().toIntOrNull() ?: 1 }
        val alpha = ((parts.last().trim().toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * 255).toInt()
        val jbColor = JBColor(Color(r, g, b, alpha), Color(r, g, b, alpha))
        colors.add(jbColor)
    }
    return colors
}

fun parseHexColor(colorHex: String): JBColor? {
    val matchResult = CSS_HEX_COLOR_SHORT_RE.find(colorHex)
        ?: CSS_HEX_COLOR_RE.find(colorHex) ?: return null

    val (_, r, g, b, a) = matchResult.groupValues
    val colorHexInt = "${a.repeatIfChar()}${r.repeatIfChar()}${g.repeatIfChar()}${b.repeatIfChar()}".toInt(16)
    return JBColor(colorHexInt, colorHexInt)
}

private fun String.repeatIfChar(): String {
    return if (length == 1) repeat(2) else this
}

fun Color.toHex(): String {
    val r = red
    val g = green
    val b = blue
    return "#${r.toString(16)}${g.toString(16)}${b.toString(16)}"
}

// parse css icons
// pattern: --un-icon: (data:...)
fun parseIcons(css: String): String? {
    CSS_UNO_ICON_PATTERN.findAll(css).forEach {
        return it.value
            .removePrefix("--un-icon:url(\"")
            .removeSuffix("\");")
    }
    return null
}

// trim css to remove comments and spaces
fun trimCss(css: String): String {
    val content = CSS_CONTENT_PATTERN.find(css)?.value ?: return css
    return content
        .replace(CSS_COMMENT_PATTERN, "")
        .replace("\n", " ")
        .let { " $it " }
}

// convert css to ast tree
fun cssToAstTree(project: Project?, css: String): List<CssLazyStylesheet> {
    val cssFile: PsiFile = PsiFileFactory.getInstance(project)
        .createFileFromText(CSSLanguage.INSTANCE, css)
    return cssFile.childrenOfType<CssLazyStylesheet>()
}

// dump ast tree to string
fun dumpAstTree(element: PsiElement, indent: Int = 0) {
    val walker = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            val indentStr = " ".repeat(indent)
            println("$indentStr$element -> ${element.elementType}")

            super.visitElement(element)

            dumpAstTree(element, indent + 1)
        }
    }
    element.acceptChildren(walker)
}