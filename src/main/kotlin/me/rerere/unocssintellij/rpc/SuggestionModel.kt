package me.rerere.unocssintellij.rpc

import kotlinx.serialization.Serializable

@Serializable
data class SuggestionCommand(
    override val action: String = "getComplete",
    val data: SuggestionCommandData,
): RpcCommand

@Serializable
data class SuggestionCommandData(
    val content: String,
    val cursor: Int = content.length
)

@Serializable
data class SuggestionResponse(
    override val action: String,
    val result: List<String>
): RpcResponse

@Serializable
data class SuggestionResult(
    val suggestions: List<List<String>>
)