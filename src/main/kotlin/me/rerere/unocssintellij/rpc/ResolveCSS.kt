package me.rerere.unocssintellij.rpc

import kotlinx.serialization.Serializable

@Serializable
data class ResolveCSSByOffsetCommand(
    override val action: String = "resolveCSSByOffset",
    val data: ResolveCSSByOffsetCommandData
) : RpcCommand

@Serializable
data class ResolveCSSByOffsetCommandData(
    val content: String,
    val cursor: Int
)

@Serializable
data class ResolveCSSCommand(
    override val action: String = "resolveCSS",
    val data: ResolveCSSCommandData
) : RpcCommand

@Serializable
data class ResolveCSSCommandData(
    val content: String
)

@Serializable
data class ResolveCSSResponse(
    override val action: String,
    val result: ResolveCSSResult
) : RpcResponse

@Serializable
data class ResolveCSSResult(
    val css: String,
    val layers: List<String>,
)