package me.rerere.unocssintellij.fold

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import me.rerere.unocssintellij.lang.psi.UnocssClassValue

class UnocssFold : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        val classValues = PsiTreeUtil.findChildrenOfType(root, UnocssClassValue::class.java)
        if(classValues.isNotEmpty()) {
            classValues.forEach {
                descriptors.add(FoldingDescriptor(it, it.textRange))
            }
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        return "Unocss"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return true
    }
}