package me.rerere.unocssintellij.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTokenType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.util.getMatchedPositions
import me.rerere.unocssintellij.util.isUnocssCandidate

class UnocssClassAttributeFoldBuilder : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (quick) return emptyArray()

        val service = root.project.service<UnocssService>()
        val file = root.containingFile
        if (!service.isProcessRunning()) return emptyArray()

        val result = runBlocking {
            withTimeout(1000) {
                service.resolveAnnotations(root.containingFile.virtualFile, root.text)
            }
        } ?: return emptyArray()

        val matchedPositions = getMatchedPositions(root.text, result)
        if (matchedPositions.isEmpty()) return emptyArray()

        val descriptors = mutableListOf<FoldingDescriptor>()
        matchedPositions.forEach { matchedPosition ->
            val element = file.findElementAt(matchedPosition.start) ?: return@forEach
            val elementType = element.elementType
            if (!element.isUnocssCandidate()) return@forEach

            if(elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
                val parent = element.parent
                descriptors.add(FoldingDescriptor(parent, parent.textRange))
            }
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}