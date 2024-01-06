package me.rerere.unocssintellij.settings

import com.intellij.lang.javascript.psi.impl.JSCallExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSVarStatementImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "me.rerere.unocssintellij.settings.UnocssSettingsState",
    storages = [Storage("UnocssIntellij.xml")]
)
class UnocssSettingsState : PersistentStateComponent<UnocssSettingsState> {

    companion object {
        val instance: UnocssSettingsState
            get() = ApplicationManager.getApplication().service<UnocssSettingsState>()

        private var jsLiteralMatchPatterns = listOf<Regex>()

        fun updateJsLiteralMatchPatterns() {
            jsLiteralMatchPatterns = instance
                .jsLiteralMatchRegex
                .split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull { kotlin.runCatching { Regex(it) }.getOrNull() }
        }

        init {
            updateJsLiteralMatchPatterns()
        }

        fun isMatchedJsLiteral(element: PsiElement): Boolean {
            val expression = element.parentOfTypes(
                JSCallExpressionImpl::class, // 调用函数
                JSVarStatementImpl::class, // 定义变量
            ) ?: return false
            val text = expression.text
            println(text)
            return jsLiteralMatchPatterns.any { regex ->
                regex.matches(text)/*.also {
                    println("regex: $regex, match: $it")
                }*/
            }
        }
    }

    var enable = true
    var matchType = MatchType.PREFIX
    var maxItems = 50
    var remToPxPreview = true
    var remToPxRatio = 16.0
    var colorPreviewType = ColorPreviewType.INLAY_HINT
    var jsLiteralMatchRegex = """
        cva\([\s\S]*?\)
        cn\([\s\S]*?\)
    """.trimIndent()

    override fun getState(): UnocssSettingsState = this

    override fun loadState(state: UnocssSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    enum class ColorPreviewType {
        NONE,
        LINE_MARKER,
        INLAY_HINT
    }

    enum class MatchType {
        PREFIX,
        FUZZY
    }
}