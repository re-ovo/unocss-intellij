package me.rerere.unocssintellij.rpc

import kotlinx.serialization.Serializable

@Serializable
class ResolveConfigCommand(
    override val action: String = "resolveConfig"
) : RpcCommand