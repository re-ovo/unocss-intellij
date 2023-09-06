package me.rerere.unocssintellij

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import me.rerere.unocssintellij.rpc.ResolveConfigResult

object UnocssConfigManager {

    object Transformers {
        const val ATTRIBUTIFY_JSX = "@unocss/transformer-attributify-jsx"

        const val DIRECTIVES = "@unocss/transformer-directives"
    }

    object Presets {
        const val ATTRIBUTIFY = "@unocss/preset-attributify"
    }

    var config: ResolveConfigResult? = null
        private set

    val theme: JsonObject get() = config?.theme ?: JsonNull.INSTANCE.asJsonObject

    private var flatTheme: MutableMap<String, String> = hashMapOf()

    val themeKeys: Set<String>
        get() = flatTheme.keys

    val themeEntries: Set<Map.Entry<String, String>>
        get() = flatTheme.entries

    fun updateConfig(config: ResolveConfigResult) {
        this.config = config

        println("[UnoCSS] Presets: ${config.presets.joinToString { it.name }}")
        println("[UnoCSS] Transformers: ${config.transformers.joinToString { it.name }}")

        updateThemeKeys()
    }

    val hasPresetAttributify: Boolean
        get() = hasPreset(Presets.ATTRIBUTIFY)

    val hasTransformerDirective: Boolean
        get() = hasTransformer(Transformers.DIRECTIVES)

    fun hasPreset(preset: String): Boolean {
        return config?.presets?.any { it.name == preset } ?: false
    }

    fun hasTransformer(transformer: String): Boolean {
        return config?.transformers?.any { it.name == transformer } ?: false
    }

    private fun updateThemeKeys() {
        fun getKeys(element: JsonObject, result: MutableMap<String, String>, prefix: String = "") {
            element.keySet().forEach { key ->
                val value = element.get(key)
                if (value.isJsonObject) {
                    getKeys(value.asJsonObject, result, "$prefix$key.")
                } else if (value.isJsonPrimitive) {
                    result["$prefix$key"] = value.asString
                }
            }
        }

        config?.let {
            flatTheme.clear()
            getKeys(it.theme, flatTheme)
            println("[UnoCSS] Parsed ${themeKeys.size} theme keys")
        }
    }

    fun getThemeValue(key: String): String? {
        return flatTheme[key]
    }
}