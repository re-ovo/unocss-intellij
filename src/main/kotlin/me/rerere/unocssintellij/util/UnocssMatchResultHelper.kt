package me.rerere.unocssintellij.util

import me.rerere.unocssintellij.rpc.ResolveAnnotationsResult

private val lessGreaterThanSignRE = Regex("[><]")
private val spaceQuoteRE = Regex("[\\s'\"]")
private val escapeRE = Regex("""[.*+?^${"$"}{}()|\[\]\\]""")

private val attributifyRE = Regex("^\\[(.+?)~?=\"(.*)\"]\$")
private val splitWithVariantGroupRE = Regex("""([\\:]?[\s"'`;<>]|:\(|\)"|\)\s)""")
private val quotedArbitraryValuesRE = Regex("""(?:[\w&:\[\]-]|\[\S+=\S+])+\[\\?['"]?\S+?['"]]]?[\w:-]*""")
private val arbitraryPropertyRE = Regex("""\[(\\\W|[\w-])+:[^\s:]*?("\S+?"|'\S+?'|`\S+?`|[^\s:]+?)[^\s:]*?\)?]""")

private fun escapeRegExp(string: String) = string.replace(escapeRE, "\\$&")

private fun splitWithVariantGroup(string: String): List<String> {
    val result = mutableListOf<String>()

    var previousEnd = 0
    splitWithVariantGroupRE.findAll(string).forEach { match ->
        val separator: String = match.value
        val separatorStart = match.range.first
        val separatorEnd = match.range.last + 1
        val substring: String = string.substring(previousEnd, separatorStart)

        result += substring
        result += separator

        previousEnd = separatorEnd
    }

    val remaining = string.substring(previousEnd)
    result += remaining

    return result
}

data class MatchedPosition(val start: Int, val end: Int, val text: String)

/**
 * Get matched positions from [code] and [annotations]
 * Ported from VSCode plugin implementation
 *
 * see: [getMatchedPosition](https://github.com/unocss/unocss/blob/main/packages/shared-common/src/index.ts#L88C26-L88C26)
 */
fun getMatchedPositions(
    code: String,
    annotations: ResolveAnnotationsResult
): List<MatchedPosition> {
    val result = mutableListOf<MatchedPosition>()
    val attributify = mutableListOf<MatchResult>()
    val plain = mutableSetOf<String>()

    fun highlightLessGreaterThanSign(str: String) {
        if (str.matches(lessGreaterThanSignRE)) {
            Regex(escapeRegExp(str)).findAll(code).forEach {
                result += MatchedPosition(it.range.first, it.range.last, it.value)
            }
        }
    }

    annotations.matched.forEach {
        val match = attributifyRE.find(it)
        if (match == null) {
            highlightLessGreaterThanSign(it)
            plain += it
        } else if (match.groupValues[2].isBlank()) {
            highlightLessGreaterThanSign(match.groupValues[1])
            plain += match.groupValues[1]
        } else {
            attributify += match
        }
    }

    var s = 0
    splitWithVariantGroup(code).forEach {
        val e = s + it.length
        if (plain.contains(it)) {
            result += MatchedPosition(s, e, it)
        }
        s = e
    }

    quotedArbitraryValuesRE.findAll(code).forEach {
        if (plain.contains(it.value)) {
            result += MatchedPosition(it.range.first, it.range.last, it.value)
        }
    }

    arbitraryPropertyRE.findAll(code).forEach {
        if (plain.contains(it.value)) {
            val found = result.find { e ->
                it.range.first == e.start && it.range.last == e.end
            }
            if (found == null) {
                result += MatchedPosition(it.range.first, it.range.last, it.value)
            }
        }
    }

    attributify.forEach {
        val name = it.groupValues[1]
        val value = it.groupValues[2]

        val regex =
            Regex("(${escapeRegExp(name)}=)(['\"])(?:(?!\\2).)*?${escapeRegExp(value)}(?:(?!\\2).)*?")
        regex.findAll(code).forEach reg@{ match ->
            val escaped = match.groupValues[1]
            val body = match.value.substring(escaped.length)
            var bodyIndex = Regex("(?:\\b|[\\s'\"])${escapeRegExp(value)}(?:\\b|[\\s'\"])")
                .find(body)?.range?.first ?: -1
            if (bodyIndex < 0) {
                return@reg
            }
            if (spaceQuoteRE.matches(body[bodyIndex].toString())) {
                bodyIndex++
            }
            val startIdx = match.range.first + escaped.length + bodyIndex
            val endIdx = startIdx + value.length
            result += MatchedPosition(startIdx, endIdx, "[${name}=\"${value}\"]")
        }
    }

    result += annotations.extraAnnotations.map {
        MatchedPosition(it.offset, it.offset + it.length, it.className)
    }
    return result.sortedBy { it.start }
}
