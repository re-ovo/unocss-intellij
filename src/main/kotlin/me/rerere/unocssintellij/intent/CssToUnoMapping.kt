package me.rerere.unocssintellij.intent

import com.intellij.psi.css.CssDeclaration

fun transformCssDeclareToUnoClass(declaration: CssDeclaration): String? {
    val propertyName = declaration.propertyName
    val propertyValueText = declaration.value?.text ?: return null

    // TODO: Convert CSS declaration to UnoCSS class

    return null
}