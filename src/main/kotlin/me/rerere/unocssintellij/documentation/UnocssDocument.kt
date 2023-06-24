@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.documentation

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.util.completion.CssClassOrIdReferenceCompletionContributor
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementType
import com.intellij.refactoring.suggested.startOffset

private val CLASS_ATTRIBUTE_NAMES = setOf("class", "className")

class UnocssDocumentTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        if(element is XmlAttributeValue) {
            val parent = element.parent
            if(parent is XmlAttribute && parent.name in CLASS_ATTRIBUTE_NAMES) {
                val classNames = element.value
                return UnocssDocumentTarget(classNames)
            }
        }
        return null
    }
}

class UnocssDocumentTarget(private val classNames: String) : DocumentationTarget {
    override fun computePresentation(): TargetPresentation {
       return TargetPresentation.builder("Unocss Document").presentation()
    }

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer {
        this
    }

    override fun computeDocumentation(): DocumentationResult {
        return DocumentationResult.Companion.asyncDocumentation {
            DocumentationResult.documentation("""
                <html>
                    <body>
                        <h1>Unocss Document</h1>
                        <code>
                            $classNames
                            </code>
                    </body>
                </html>
            """.trimIndent())
        }
    }
}
