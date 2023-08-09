package me.rerere.unocssintellij

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

const val BUNDLE = "messages.UnocssBundle"

object UnocssBundle : DynamicBundle(BUNDLE) {
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)
}