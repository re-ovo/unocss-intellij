package me.rerere.unocssintellij.intent

import com.intellij.psi.css.CssDeclaration
import me.rerere.unocssintellij.intent.mapping.marginMapping
import me.rerere.unocssintellij.intent.mapping.paddingMapping

typealias AtomicCssGenerator = (MatchResult) -> Set<String>

fun transformCssDeclareToUnoClass(declaration: CssDeclaration): List<String> {
    val propertyName = declaration.propertyName
    val propertyValueText = declaration.value?.text ?: return emptyList()

    val result = arrayListOf<String>()
    cssDeclarationMapping[propertyName]?.forEach { (regex, atomicCssGenerator) ->
        val matchResult = regex.find(propertyValueText) ?: return@forEach
        val atomicCss = atomicCssGenerator(matchResult)
        result.addAll(atomicCss)
        println("Matched: $atomicCss")
    }

    return result
}


// Thanks GPT-4 for generating a lot of regexes for me :D
private val cssDeclarationMapping = listOf(
    marginMapping,
    paddingMapping,
).reduce { acc, map ->
    acc.putAll(map)
    acc
}