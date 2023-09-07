package me.rerere.unocssintellij.util

import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.*
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.css.impl.CssAtRuleImpl
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssFunctionImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlElementType
import com.intellij.xml.util.HtmlUtil
import me.rerere.unocssintellij.Unocss

val classTagNameVariants = setOf(
    // class
    HtmlUtil.CLASS_ATTRIBUTE_NAME,
    "className",
    ":class",
    "v-bind:class"
)

/**
 * Return if a [String] represents a class attribute
 * @see classTagNameVariants
 */
internal fun String.isClassAttribute() = this in classTagNameVariants

/**
 * Build a dummy div tag with given attributes
 *
 * Example:
 * ```kotlin
 * buildDummyDivTag("class" to "bg-blue-500") // <div class="bg-blue-500" >
 * buildDummyDivTag("text-red" to null) // <div text-red >
 * ```
 */
internal fun buildDummyDivTag(vararg attributes: Pair<String, String?>) = buildString {
    append("<div")
    attributes.forEach {
        append(" ")
        if (it.second.isNullOrBlank()) {
            append(it.first)
        } else {
            append("${it.first}=\"${it.second}\"")
        }
    }
    append(" >")
}

val annotationAcceptableElementTypes = setOf(
    XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN,
    XmlElementType.XML_NAME,
    CssElementTypes.CSS_STRING_TOKEN,
    CssElementTypes.CSS_IDENT,
)

/**
 * Return if a [PsiElement] can be annotated via highlighting or inlay hints
 */
internal fun PsiElement.isUnocssCandidate(): Boolean {
    return elementType in annotationAcceptableElementTypes
            || isLeafJsLiteral()
            || isJsProperty()
}

private val leafJsLiteralMatcher = PlatformPatterns
    .psiElement(LeafPsiElement::class.java)
    .withParent(JSLiteralExpression::class.java)

private val jsPropertyKeyMatcher = PlatformPatterns.psiElement(LeafPsiElement::class.java)
    .withParent(JSProperty::class.java)

/**
 * Return if a [PsiElement] is a *leaf* [JSLiteralExpression]
 */
internal fun PsiElement.isLeafJsLiteral() = leafJsLiteralMatcher.accepts(this)

/**
 * Return if a [PsiElement] is a *leaf* [JSProperty]
 */
internal fun PsiElement.isJsProperty() = jsPropertyKeyMatcher.accepts(this)

/**
 * Recursive (depth first) search for all elements of given classes.
 * @see PsiTreeUtil.findChildrenOfType
 */
inline fun <reified T : PsiElement> PsiElement.childrenOfTypeDeeply(): Collection<T> {
    return PsiTreeUtil.findChildrenOfType(this, T::class.java)
}

/**
 * Recursive (depth first) search for the first element of a given class.
 * @see PsiTreeUtil.findChildOfType
 */
inline fun <reified T : PsiElement> PsiElement.childOfTypeDeeply(): T? {
    return PsiTreeUtil.findChildOfType(this, T::class.java)
}

/**
 * Return if a [PsiElement] is a descendant of a [CssFunctionImpl] named "theme"
 */
internal fun PsiElement.inCssThemeFunction(): Boolean {
    if (elementType != CssElementTypes.CSS_STRING_TOKEN) {
        return false
    }
    val parent = parentOfType<CssFunctionImpl>()
    return parent != null && parent.name == "theme"
}

/**
 * Return if a [PsiElement] is a descendant of a [CssAtRuleImpl] named "screen"
 */
internal fun PsiElement.isScreenDirectiveIdent(): Boolean {
    if (elementType != CssElementTypes.CSS_IDENT && elementType != CssElementTypes.CSS_NUMBER) {
        return false
    }
    val parent = this.parent
    return parent is CssAtRuleImpl && parent.name == "@screen"
}

internal fun Project.findUnoConfigFile(): PsiFile? {
    val tsFiles = FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.allScope(this))
    val jsFiles = FileTypeIndex.getFiles(JavaScriptFileType.INSTANCE, GlobalSearchScope.allScope(this))

    val candidateFiles = (tsFiles + jsFiles)
    val configFile = candidateFiles.firstOrNull { file -> Unocss.ConfigFiles.contains(file.name) } ?: return null
    return PsiManager.getInstance(this).findFile(configFile)
}

object UnoConfigHelper {
    val screenPrefixes = setOf("lt-", "at-")
    val defaultApplyVariable = setOf("--at-apply", "--uno-apply", "--uno")

    /**
     * Find the theme config object in config file
     */
    fun findThemeConfig(element: PsiElement): JSElement? {
        val configFile = element.project.findUnoConfigFile()

        configFile?.childrenOfTypeDeeply<JSObjectLiteralExpression>()
            ?.let { objectLiterals ->
                for (jsObjectLiteralExpression in objectLiterals) {
                    val themeProperty = jsObjectLiteralExpression.findProperty("theme") ?: continue
                    val themeValue = themeProperty.value
                    if (themeValue is JSObjectLiteralExpression) {
                        return themeValue
                    }
                    // 如果是引用，找到引用的对象
                    if (themeValue is JSReferenceExpression) {
                        val resolved = themeValue.resolve()
                        if (resolved is ES6ImportedBinding) {
                            return resolved.findReferencedElements().firstNotNullOfOrNull {
                                it.childOfTypeDeeply<JSObjectLiteralExpression>()
                            }
                        } else {
                            val objectLiteral = resolved?.childOfTypeDeeply<JSObjectLiteralExpression>()
                            if (objectLiteral != null) {
                                return objectLiteral
                            }
                        }
                    }
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

    /**
     * Recursive (depth first) search for theme config property by given [objectPath].
     *
     * A [objectPath] is a list of keys, could be split from nested object syntax, for example:
     *
     * `colors.red.500` -> `["colors", "red", "500"]`
     */
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