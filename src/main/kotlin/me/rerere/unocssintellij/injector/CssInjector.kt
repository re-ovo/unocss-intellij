package me.rerere.unocssintellij.injector

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import me.rerere.unocssintellij.lang.UnocssLang

class UnocssInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context is XmlAttributeValue && shouldInjectXmlAttr(context)) {
            registrar
                .startInjecting(UnocssLang.INSTANCE)
                .addPlace(
                    null,
                    null,
                    context as PsiLanguageInjectionHost,
                    TextRange(0, (context as PsiElement).textLength)
                )
                .doneInjecting()
        }

        if(context is JSLiteralExpression && context.parent is JSVariable) {
            val value = context.value
            // println("Injecting: $value, ${shouldInjectJsString(value.toString())}")
            if(value is String && shouldInjectJsString(value)) {
                registrar
                    .startInjecting(UnocssLang.INSTANCE)
                    .addPlace(
                        null,
                        null,
                        context as PsiLanguageInjectionHost,
                        TextRange(0, (context as PsiElement).textLength)
                    )
                    .doneInjecting()
            }
        }
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(
            XmlAttributeValue::class.java,
            JSLiteralExpression::class.java
        )
    }

    private val classNames = setOf("class", "className")
    private fun shouldInjectXmlAttr(context: PsiElement): Boolean {
        val parent = context.parent
        if (parent is XmlAttribute) {
            val name = parent.name
            if (name in classNames) {
                return true
            }
        }
        return false
    }

    private val classNameRegex = Regex("^([a-zA-Z0-9_#-]+(\\s+|\$))+")
    private fun shouldInjectJsString(string: String) = classNameRegex.matches(string.trim())
}
