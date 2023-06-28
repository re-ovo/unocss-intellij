package me.rerere.unocssintellij.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import me.rerere.unocssintellij.lang.parser.UnocssParser
import me.rerere.unocssintellij.lang.psi.UnocssTypes
import org.jetbrains.annotations.NonNls


class UnocssLang : Language("Unocss") {
    companion object {
        @JvmStatic
        val INSTANCE = UnocssLang()
    }
}

class UnocssFileType : LanguageFileType(UnocssLang.INSTANCE) {
    override fun getName(): String = "Unocss file"
    override fun getDescription(): String = "Unocss lang file"
    override fun getDefaultExtension(): String = "unocss"
    override fun getIcon(): javax.swing.Icon? = null

    companion object {
        @JvmStatic
        val INSTANCE = UnocssFileType()
    }
}

open class UnocssTokenType(debugName: @NonNls String) : IElementType(debugName, UnocssLang.INSTANCE)

open class UnocssElementType(debugName: @NonNls String) : IElementType(debugName, UnocssLang.INSTANCE)

class UnocssFlexAdapter : FlexAdapter(UnocssLexer(null))

class UnocssFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, UnocssLang.INSTANCE) {
    override fun getFileType(): FileType {
        return UnocssFileType.INSTANCE
    }
}

class UnocssParserDefinition : ParserDefinition {
    companion object {
        private val FILE = IFileElementType(UnocssLang.INSTANCE)
    }

    override fun createLexer(project: Project?): Lexer {
        return UnocssFlexAdapter()
    }

    override fun createParser(project: Project?): PsiParser {
        return UnocssParser()
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode?): PsiElement {
        return UnocssTypes.Factory.createElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return UnocssFile(viewProvider)
    }
}