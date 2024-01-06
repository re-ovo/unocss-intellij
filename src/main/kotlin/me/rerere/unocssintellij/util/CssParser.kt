package me.rerere.unocssintellij.util

import com.google.common.cache.CacheBuilder
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

private val colorParseCache by lazy {
    CacheBuilder.newBuilder()
        .maximumSize(64)
        .build<String, Set<JBColor>>()
}
private val iconParseCache by lazy {
    CacheBuilder.newBuilder()
        .maximumSize(64)
        .build<String, String?>()
}

private val CSS_RGBA_COLOR_PATTERN =
    """rgba\(\d{1,3},\s*\d{1,3},\s*\d{1,3},\s*var\(--[a-z-]+\)\)|rgba\(\d{1,3},\s*\d{1,3},\s*\d{1,3},\s*0\.\d+\)|rgb\(\d{1,3}\s*\d{1,3}\s*\d{1,3}\s*/\s*var\(--[a-z-]+\)\)""".toRegex()
private val CSS_VAR_DEF_PATTERN =
    """--[\w-]+:\s*\d+(\.\d+)?;""".toRegex()

private val CSS_COMMENT_PATTERN = """/\*.*?\*/""".toRegex()
private val CSS_CONTENT_PATTERN = """(?<=\{)[^}]*(?=})""".toRegex()

private val CSS_UNO_ICON_PATTERN = """--un-icon:\s?url\(.+\);""".toRegex()

private val CSS_HEX_COLOR_SHORT_RE = "^#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])?$".toRegex()
private val CSS_HEX_COLOR_RE = "^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})?$".toRegex()

// parse colors from css
// pattern: rgba(255, 255, 255, var(--opacity))
// pattern: rgba(255, 255, 255, 0.5)
// pattern: rgb(219 39 119 / var(--un-bg-opacity))
fun parseColors(css: String): Set<JBColor> {
    if (colorParseCache.getIfPresent(css) != null) {
        // Parse color is slow, so we use cache
        return colorParseCache.getIfPresent(css) ?: emptySet()
    }

    val colors = mutableSetOf<JBColor>()
    val varDefs = CSS_VAR_DEF_PATTERN.findAll(css)
        .map {
            val varName = it.value.substringBefore(":").trim()
            val varValue = it.value.removeSuffix(";").substringAfter(":").trim().toFloatOrNull() ?: 1f
            varName to varValue
        }
        .toMap()

    CSS_RGBA_COLOR_PATTERN.findAll(css).forEach { matchResult ->
        val value = matchResult.value.removePrefix("rgba(").removePrefix("rgb(").removeSuffix(")")
        val parts = if (value.contains("/")) {
            value.split(" ", "/").filter { it.isNotBlank() }
        } else {
            value.split(",")
        }
        val (r, g, b) = parts.map { it.trim().toIntOrNull() ?: 1 }
        val alphaPart = parts.getOrNull(3) ?: parts.getOrNull(2)?.split("/")?.getOrNull(1) ?: ""
        val alpha = if (alphaPart.startsWith("var(")) {
            val varName = alphaPart.removePrefix("var(").removeSuffix(")").trim()
            ((varDefs[varName] ?: 1f).coerceIn(0f, 1f) * 255).toInt()
        } else {
            ((alphaPart.trim().toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * 255).toInt()
        }
        val jbColor = JBColor(Color(r, g, b, alpha), Color(r, g, b, alpha))
        colors.add(jbColor)
    }
    colorParseCache.put(css, colors)
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

fun Color.toHex(useAlpha: Boolean = false): String {
    fun Int.hex(): String {
        return toString(16).padStart(2, '0')
    }

    val r = red
    val g = green
    val b = blue
    val a = if (useAlpha) alpha else 255
    return "#${r.hex()}${g.hex()}${b.hex()}${if (useAlpha) a.hex() else ""}"
}

// parse css icons
// pattern: --un-icon: (data:...)
fun parseIcons(css: String): String? {
    if (iconParseCache.getIfPresent(css) != null) {
        // Parse icon is slow, so we use cache
        return iconParseCache.getIfPresent(css)
    }
    CSS_UNO_ICON_PATTERN.findAll(css).forEach {
        return it.value
            .removePrefix("--un-icon:url(\"")
            .removeSuffix("\");")
            .also {
                iconParseCache.put(css, it)
            }
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