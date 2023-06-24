package me.rerere.unocssintellij.rpc

import kotlinx.serialization.Serializable

sealed interface RpcCommand {
    val action: String
}

sealed interface RpcResponse {
    val action: String
}

@Serializable
class RpcResponseUnit(override val action: String) : RpcResponse