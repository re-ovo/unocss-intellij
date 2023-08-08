package me.rerere.unocssintellij.util

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