package me.rerere.unocssintellij.rpc

import kotlinx.serialization.Serializable

@Serializable
class ResolveConfig(
    override val action: String = "resolveConfig"
) : RpcCommand