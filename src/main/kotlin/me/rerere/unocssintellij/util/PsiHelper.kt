package me.rerere.unocssintellij.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.xml.util.HtmlUtil

val classTagNameVariants = setOf(
    HtmlUtil.CLASS_ATTRIBUTE_NAME,
    "className",
    ":class",
    "v-bind:class"
)

fun isClassAttribute(attributeName: String) = classTagNameVariants.contains(attributeName)

fun buildDummyDivTag(vararg attributes: Pair<String, String?>) = buildString {
    append("<div")
    attributes.forEach {
        append(" ")
        if (it.second.isNullOrBlank()) {
            append(it.first)
        } else {
            append("${it.first}=\"${it.second}\"")
        }
    }
    append(" >")
}

inline fun <reified T : PsiElement> PsiElement.childrenOfTypeDeeply(): Collection<T> {
    return PsiTreeUtil.findChildrenOfType(this, T::class.java)
}

inline fun <reified T : PsiElement> PsiElement.childOfTypeDeeply(): T? {
    return PsiTreeUtil.findChildOfType(this, T::class.java)
}

private val jsStringMatcher = PlatformPatterns
    .psiElement(LeafPsiElement::class.java)
    .withParent(JSLiteralExpression::class.java)

fun isJsString(element: PsiElement) = jsStringMatcher.accepts(element)