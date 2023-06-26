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

private val CSS_UNO_ICON_PATTERN = """--un-icon:\s?url\(.+\);""".toRegex()

// parse colors from css
// pattern: rgba(255, 255, 255, var(--opacity))
fun parseColors(css: String): Set<JBColor> {
    val colors = mutableSetOf<JBColor>()
    CSS_RGBA_COLOR_PATTERN.findAll(css).forEach {
        val value = it.value.removePrefix("rgba(").removeSuffix(")")
        val (r, g, b) = value.split(",").map { it.trim().toIntOrNull() ?: 1 }
        val color = JBColor(Color(r, g, b), Color(r, g, b).darker())
        colors.add(color)
    }
    return colors
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
    return css
        .replace(CSS_COMMENT_PATTERN, "")
        .replace("\n", " ")
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