package me.rerere.unocssintellij.references

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.css.impl.CssFunctionImpl
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import me.rerere.unocssintellij.Unocss

object UnoConfigPsiHelper {

    fun inCssThemeFunction(element: PsiElement): Boolean {
        val parent = PsiTreeUtil.getParentOfType(element, CssFunctionImpl::class.java)
        return parent != null && parent.name == "theme"
    }

    fun findThemeConfig(element: PsiElement): JSProperty? {
        val project = element.project
        val tsFiles = FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.allScope(project))
        val jsFiles = FileTypeIndex.getFiles(JavaScriptFileType.INSTANCE, GlobalSearchScope.allScope(project))

        val candidateFiles = (tsFiles + jsFiles)
        val configFile = candidateFiles.firstOrNull { file -> Unocss.ConfigFiles.contains(file.name) } ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(configFile) ?: return null

        PsiTreeUtil.findChildrenOfType(psiFile, JSObjectLiteralExpression::class.java)
            .forEach { jsObjectLiteralExpression ->
                val themeProperty = jsObjectLiteralExpression.findProperty("theme")
                if (themeProperty != null) {
                    return themeProperty
                }
            }

        return null
    }

    fun findThemeConfigValue(element: PsiElement, objectPath: List<String>): String? {
        val themeConfig = findThemeConfig(element) ?: return null
        val themeConfigValue = themeConfig.value
        if (themeConfigValue !is JSObjectLiteralExpression) {
            return null
        }
        val property = findThemeConfigProperty(themeConfigValue, objectPath, 0) ?: return null
        val propertyValue = property.value ?: return null
        if (propertyValue is JSLiteralExpression && propertyValue.isStringLiteral) {
            return propertyValue.stringValue
        }
        return null
    }

    fun findThemeConfigProperty(
        themeConfig: JSObjectLiteralExpression,
        objectPath: List<String>,
        index: Int,
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