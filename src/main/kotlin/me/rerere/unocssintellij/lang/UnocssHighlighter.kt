package me.rerere.unocssintellij.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import me.rerere.unocssintellij.lang.psi.UnocssTypes

class UnocssHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer {
        return UnocssFlexAdapter()
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when(tokenType) {
            UnocssTypes.CLASSNAME -> arrayOf(
                DefaultLanguageHighlighterColors.STRING
            )
            else -> arrayOf()
        }
    }
}