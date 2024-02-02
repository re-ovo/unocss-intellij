package me.rerere.unocssintellij.intent.mapping

import me.rerere.unocssintellij.intent.AtomicCssGenerator

val colorMapping: HashMap<String, Map<Regex, AtomicCssGenerator>> = hashMapOf(
    "color" to mapOf(
        // color: #{} | rgb() | rgba() | hsl() | hsla() | transparent | currentColor
        Regex("^(#[0-9a-fA-F]{3,6}|rgb\\(.*\\)|rgba\\(.*\\)|hsl\\(.*\\)|hsla\\(.*\\)|transparent|currentColor)\$") to { matchResult ->
            setOf("text-${matchResult.groupValues[1]}")
        }
    ),
)