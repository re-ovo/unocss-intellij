package me.rerere.unocssintellij

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger

object Unocss {
    // Logger
    val Logger = logger<Unocss>()

    // Config file name
    val ConfigFiles: Set<String> = listOf(
        "uno.config",
        "vite.config",
        "svelte.config",
        "iles.config",
        "astro.config",
        "nuxt.config",
    ).flatMap {
        listOf("$it.js", "$it.ts")
    }.toSet()

    // Global JSON instance
    val GSON: Gson by lazy { GsonBuilder().create() }
}