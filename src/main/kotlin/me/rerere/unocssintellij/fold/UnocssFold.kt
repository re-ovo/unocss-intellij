package me.rerere.unocssintellij.fold

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement

class UnocssFold : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        TODO("Not yet implemented")
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        TODO("Not yet implemented")
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        TODO("Not yet implemented")
    }
}