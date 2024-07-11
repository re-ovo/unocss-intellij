@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import me.rerere.unocssintellij.rpc.SuggestionItem
import me.rerere.unocssintellij.util.IconResources
import me.rerere.unocssintellij.util.appendHighlightedCss

class UnocssCompletionLookupSymbol(private val suggestion: SuggestionItem, private val project: Project) :
    DocumentationSymbol {

    override fun createPointer() = Pointer.hardPointer(this)

    override fun getDocumentationTarget(): DocumentationTarget {
        return object : DocumentationTarget {

            override fun computePresentation(): TargetPresentation {
                return TargetPresentation
                    .builder("Unocss Document")
                    .locationText("Unocss", IconResources.PluginIcon)
                    .presentation()
            }

            override fun createPointer() = Pointer.hardPointer(this)

            override fun computeDocumentation(): DocumentationResult? {
                val suggestedCss = suggestion.css.takeIf(String::isNotBlank) ?: return null

                return DocumentationResult.asyncDocumentation {
                    DocumentationResult.documentation(buildString {
                        append(DocumentationMarkup.DEFINITION_START)
                        appendHighlightedCss(project, suggestedCss)
                        append(DocumentationMarkup.DEFINITION_END)

                        append(DocumentationMarkup.CONTENT_START)
                        append(DocumentationMarkup.CONTENT_END)
                    })
                }
            }
        }
    }
}

class UnocssThemeConfigCompletionLookupSymbol(
    private val themeConfigValue: String,
    private val project: Project
) : DocumentationSymbol {
    override fun createPointer() = Pointer.hardPointer(this)

    override fun getDocumentationTarget(): DocumentationTarget {
        return object : DocumentationTarget {
            override fun computePresentation(): TargetPresentation {
                return TargetPresentation
                    .builder("Unocss Theme Configuration")
                    .locationText("Unocss Theme", IconResources.PluginIcon)
                    .presentation()
            }

            override fun createPointer() = Pointer.hardPointer(this)

            override fun computeDocumentation(): DocumentationResult? {
                return DocumentationResult.asyncDocumentation {
                    DocumentationResult.documentation(buildString {
                        append(DocumentationMarkup.DEFINITION_START)
                        append(themeConfigValue)
                        append(DocumentationMarkup.DEFINITION_END)

                        append(DocumentationMarkup.CONTENT_START)
                        append(DocumentationMarkup.CONTENT_END)
                    })
                }
            }
        }
    }

}