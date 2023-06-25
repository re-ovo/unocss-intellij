@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.documentation

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import me.rerere.unocssintellij.UnocssService

class UnocssDocumentTargetProviderOffset : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): MutableList<out DocumentationTarget> {
        println("file: $file $offset")

        val service = file.project.service<UnocssService>()
        val result = service.resolveCss(file, offset)

        return if (result.css.isNotEmpty()) {
            val readableCSS = parseCss(result.css)
            println(readableCSS)
            val target = UnocssDocumentTarget(readableCSS.replace("\n", "<br>").replace(" ", "&nbsp;"))
            mutableListOf(target)
        } else {
            mutableListOf()
        }
    }
}

class UnocssDocumentTarget(val doc: String) : DocumentationTarget {
    override fun computePresentation(): TargetPresentation {
        return TargetPresentation
            .builder("Unocss Document")
            .presentation()
    }

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer {
        this
    }

    override fun computeDocumentation(): DocumentationResult {
        return DocumentationResult.Companion.asyncDocumentation {
            DocumentationResult.documentation(buildString {
                append("<html><body>")
                append(doc)
                append("</body></html>")
            })
        }
    }
}

// 好蠢，能不能实现PsiElement高亮啊
private fun parseCss(css: String): String {
    return buildString {
        css.lines().forEach { line ->
            val isComment = line.startsWith("/*")
            println("line: $line $isComment")
            if (isComment) {
                append(line)
                append("\n")
            } else {
                val cssSelector = line.substringBefore("{").trim()
                println(cssSelector)
                val cssContent = line
                    .substringAfter("{")
                    .substringBeforeLast("}")
                    .trim()
                    .split(";")

                append(cssSelector)
                append(" {\n")
                cssContent
                    .filter {
                        it.isNotEmpty()
                    }.forEach { content ->
                        append("    ")
                        append(content)
                        append(";\n")
                    }
                append("}\n")
            }
        }
    }
}