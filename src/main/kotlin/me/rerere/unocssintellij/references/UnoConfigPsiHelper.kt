package me.rerere.unocssintellij.references

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.css.impl.CssAtRuleImpl
import com.intellij.psi.css.impl.CssFunctionImpl
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import me.rerere.unocssintellij.Unocss
import me.rerere.unocssintellij.util.childOfTypeDeeply
import me.rerere.unocssintellij.util.childrenOfTypeDeeply

object UnoConfigPsiHelper {
    val screenPrefixes = setOf("lt-", "at-")
    val defaultApplyVariable = setOf("--at-apply", "--uno-apply", "--uno")

    fun inCssThemeFunction(element: PsiElement): Boolean {
        val parent = element.parentOfType<CssFunctionImpl>()
        return parent != null && parent.name == "theme"
    }

    fun inScreenDirective(element: PsiElement): Boolean {
        val parent = element.parentOfType<CssAtRuleImpl>()
        return parent != null && parent.name == "@screen"
    }

    fun findUnoConfigFile(element: PsiElement): PsiFile? {
        val project = element.project
        val tsFiles = FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.allScope(project))
        val jsFiles = FileTypeIndex.getFiles(JavaScriptFileType.INSTANCE, GlobalSearchScope.allScope(project))

        val candidateFiles = (tsFiles + jsFiles)
        val configFile = candidateFiles.firstOrNull { file -> Unocss.ConfigFiles.contains(file.name) } ?: return null
        return PsiManager.getInstance(project).findFile(configFile)
    }

    fun findThemeConfig(element: PsiElement): JSElement? {
        val configFile = findUnoConfigFile(element)

        configFile?.childrenOfTypeDeeply<JSObjectLiteralExpression>()
            ?.forEach { jsObjectLiteralExpression ->
                val themeProperty = jsObjectLiteralExpression.findProperty("theme")
                if (themeProperty != null) {

                    // 如果是引用，找到引用的对象
                    if (themeProperty.value is JSReferenceExpression) {
                        val reference = themeProperty.value as JSReferenceExpression
                        val resolved = reference.resolve()
                        val objectLiteral = resolved?.childOfTypeDeeply<JSObjectLiteralExpression>()
                        if (objectLiteral != null) {
                            return objectLiteral
                        }
                    }

                    return themeProperty.value
                }
            }

        return null
    }

    fun findThemeConfigValue(element: PsiElement, objectPath: List<String>): String? {
        val themeConfigValue = findThemeConfig(element) ?: return null
        if (themeConfigValue !is JSObjectLiteralExpression) {
            return null
        }
        val property = findThemeConfigProperty(themeConfigValue, objectPath) ?: return null
        val propertyValue = property.value ?: return null
        if (propertyValue is JSLiteralExpression && propertyValue.isStringLiteral) {
            return propertyValue.stringValue
        }
        return null
    }

    fun findThemeConfigProperty(
        themeConfig: JSObjectLiteralExpression,
        objectPath: List<String>,
        index: Int = 0,
    ): JSProperty? {
        if (index >= objectPath.size) {
            return null
        }

        val key = objectPath[index]
        val property = themeConfig.findProperty(key) ?: return null

        if (index == objectPath.size - 1) {
            return property
        }

        val value = property.value
        if (value is JSObjectLiteralExpression) {
            return findThemeConfigProperty(value, objectPath, index + 1)
        }

        return null
    }
}