package me.rerere.unocssintellij.injector

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.javascript.psi.JSConditionalExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.lang.UnocssLang

class UnocssInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val service = context.project.service<UnocssService>()

        // 判断是否是补全触发的语言注入
        // 在这种情况下，注入条件放宽，从而使得能够在未完成一个完整的类名时也能够触发注入
        val phase = CompletionServiceImpl.getCompletionPhase()
        val isCompletionPhase =
            phase is CompletionPhase.EmptyAutoPopup
                    || phase is CompletionPhase.CommittingDocuments
                    || phase is CompletionPhase.BgCalculation

        // 如果是xml属性, 注入语法
        if (context is XmlAttributeValue && shouldInjectXmlAttr(context)) {
            val value = context.value
            if((isCompletionPhase && service.isFirstSegmentCss(value)) || service.isRealCssClassName(value)) {
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

        // 如果是JS字符串，并且上级是变量定义或者三元表达式，注入语法
        if(context is JSLiteralExpression && (context.parent is JSVariable || context.parent is JSConditionalExpression)) {
            val value = context.value
            // 确保字符串内容符合类名列表规则
            // 防止匹配到其他字符串，例如url或者i18n key
            if(value is String && shouldInjectJsString(value)) {
                if((isCompletionPhase && service.isFirstSegmentCss(value)) || service.isRealCssClassName(value)) {
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
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(
            XmlAttributeValue::class.java,
            JSLiteralExpression::class.java
        )
    }

    private fun UnocssService.isRealCssClassName(text: String) : Boolean {
        val cssResolved = runBlocking {
            withTimeout(300) {
                resolveCss(null, text)?.css?.isNotBlank() ?: false
            }
        }
        return cssResolved
    }

    private fun UnocssService.isFirstSegmentCss(text: String): Boolean {
        val segments = text.trim().split(Regex("\\s+"))
        if(segments.size <= 1) return true // 只有一个片段，不需要检查，强制注入
        val firstCss = segments.first()
        return runBlocking {
            withTimeout(300) {
                resolveCss(null, firstCss)?.css?.isNotBlank() ?: false
            }
        }
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

    private val classNameRegex = Regex("^([a-zA-Z0-9_#-:]+(\\s+|\$))+")
    private fun shouldInjectJsString(string: String) = classNameRegex.matches(string.trim())
}
