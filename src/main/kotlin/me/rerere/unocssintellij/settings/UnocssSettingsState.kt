package me.rerere.unocssintellij.settings

import com.intellij.lang.javascript.psi.impl.JSCallExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSVarStatementImpl
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "me.rerere.unocssintellij.settings.UnocssSettingsState",
    storages = [Storage("UnocssIntellij.xml")]
)
@Service(Service.Level.PROJECT)
class UnocssSettingsState : PersistentStateComponent<UnocssSettingsState> {

    companion object {
        @JvmStatic
        fun of(project: Project): UnocssSettingsState = project.service()
    }

    // ====== Documentation ======
    var includeMdnDocs = true
    var remToPxPreview = true
    var remToPxRatio = 16.0

    // ====== Annotation ======
    var enableUnderline = true
    var colorAndIconPreviewType = ColorAndIconPreviewType.INLAY_HINT

    // ====== Autocomplete ======
    var matchType = MatchType.PREFIX
    var maxItems = 50

    // ====== Code Folding ======
    var codeDefaultFolding = false
    var foldingCodeLength = 10
    var foldingPlaceholder = "[UnoCSS]"

    // ====== Matcher ======
    var jsLiteralMatchRegexPatterns = """
            cva\([\s\S]*?\)
            cn\([\s\S]*?\)
        """.trimIndent()

    private var jsLiteralMatchRegexes = listOf<Regex>()

    init {
        updateJsLiteralMatchPatterns(jsLiteralMatchRegexPatterns)
    }

    fun updateJsLiteralMatchPatterns(patterns: String) {
        jsLiteralMatchRegexes = patterns
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    fun isMatchedJsLiteral(element: PsiElement): Boolean {
        val expression = element.parentOfTypes(
            JSCallExpressionImpl::class, // 调用函数
            JSVarStatementImpl::class, // 定义变量
        ) ?: return false
        val text = expression.text
        // println(text)
        return jsLiteralMatchRegexes.any { regex ->
            regex.matches(text)/*.also {
                    println("regex: $regex, match: $it")
                }*/
        }
    }

    override fun getState() = this

    override fun loadState(state: UnocssSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    enum class ColorAndIconPreviewType {
        NONE,
        LINE_MARKER,
        INLAY_HINT
    }

    enum class MatchType {
        PREFIX,
        FUZZY
    }
}