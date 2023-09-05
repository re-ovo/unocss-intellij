package me.rerere.unocssintellij.rpc

data class RpcCommand<T>(
    private val id: String,
    private val action: String,
    private val data: T
)

enum class RpcAction(val key: String) {
    ResolveConfig("resolveConfig"),
    UpdateSettings("updateSettings"),
    GetComplete("getComplete"),
    ResolveCss("resolveCSS"),
    ResolveCssByOffset("resolveCSSByOffset"),
    ResolveAnnotations("resolveAnnotations"),
    ResolveBreakpoints("resolveBreakpoints"),
}

data class UpdateSettingsCommandData(
    val matchType: String
)

data class GetCompleteCommandData(
    val content: String,
    val cursor: Int = content.length,
    val maxItems: Int,
)

data class SuggestionItem(
    val className: String,
    val css: String
)

class SuggestionItemList : ArrayList<SuggestionItem>()


data class ResolveCSSByOffsetCommandData(
    val content: String,
    val cursor: Int
)

data class ResolveCSSCommandData(
    val content: String
)

data class ResolveCSSResult(
    val css: String,
    val layers: List<String>,
    val matchedTokens: Set<String>,
)

data class ResolveAnnotationsCommandData(
    val id: String = "",
    val content: String
)

data class ResolveAnnotationsResult(
    val matched: Set<String>,
    val extraAnnotations: List<HighlightAnnotation>
) {
    data class HighlightAnnotation(val offset: Int, val length: Int, val className: String)
}

data class ResolveBreakpointsResult(
    val breakpoints: Map<String, String>
)