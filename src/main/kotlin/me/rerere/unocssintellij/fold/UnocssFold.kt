package me.rerere.unocssintellij.fold

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.PsiTreeUtil
import me.rerere.unocssintellij.lang.UnocssLang
import me.rerere.unocssintellij.lang.psi.UnocssClassValue

class UnocssFold : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(root.project)
        val descriptors = mutableListOf<FoldingDescriptor>()

        val folderGroup = FoldingGroup.newGroup("unocss")

        root.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                injectedLanguageManager.enumerate(element) { injectedPsi, _ ->
                    if(injectedPsi.language == UnocssLang.INSTANCE) {
                        val hostRange = injectedLanguageManager.injectedToHost(
                            injectedPsi,
                            injectedPsi.textRange
                        )
                        if(hostRange.length == 0) return@enumerate
                        if(descriptors.any { it.range == hostRange }) {
                            return@enumerate
                        }
                        descriptors.add(FoldingDescriptor(element.node, hostRange, folderGroup))
                    }
                }
                super.visitElement(element)
            }
        })

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        return node.text.trim('"')
            .let {
                if(it.length > 20) {
                    it.substring(0, 20) + "..."
                } else {
                    it
                }
            }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return true
    }
}