package me.rerere.unocssintellij.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTokenType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.getMatchedPositions
import me.rerere.unocssintellij.util.isUnocssCandidate

class UnocssClassAttributeFoldBuilder : FoldingBuilderEx() {
    private val group = FoldingGroup.newGroup("unocss-class-attribute")

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (quick) return emptyArray()

        val service = root.project.service<UnocssService>()
        val file = root.containingFile
        if (!service.isProcessRunning()) return emptyArray()

        val result = runBlocking {
            withTimeoutOrNull(500) {
                service.resolveAnnotations(root.containingFile.virtualFile, root.text)
            }
        } ?: return emptyArray()

        val matchedPositions = getMatchedPositions(root.text, result)
        if (matchedPositions.isEmpty()) return emptyArray()

        val descriptors = mutableListOf<FoldingDescriptor>()
        val minLength = UnocssSettingsState.instance.foldingCodeLength
        matchedPositions.forEach { matchedPosition ->
            val element = file.findElementAt(matchedPosition.start) ?: return@forEach
            val elementType = element.elementType
            if (!element.isUnocssCandidate()) return@forEach

            if (elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
                if (element.node.textLength > minLength) {
                    descriptors.add(FoldingDescriptor(element.node, element.textRange, group))
                }
            }
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = UnocssSettingsState.instance.foldingPlaceholder

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return UnocssSettingsState.instance.codeDefaultFolding
    }
}