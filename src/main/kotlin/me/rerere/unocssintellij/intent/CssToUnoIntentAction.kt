package me.rerere.unocssintellij.intent

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.css.CssBlock
import com.intellij.psi.css.CssClass
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssRuleset
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import me.rerere.unocssintellij.util.childOfTypeDeeply

class CssToUnoIntentAction : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String {
        return "UnoCSS"
    }

    override fun getText(): String {
        return "Convert to UnoCSS"
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return element.elementType == CssElementTypes.CSS_IDENT && element.parent is CssClass
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val ruleSet = element.parentOfType<CssRuleset>() ?: return
        val declareBlock = ruleSet.childOfTypeDeeply<CssBlock>() ?: return
        val declarations = declareBlock.declarations
        val transformedCssNames = declarations
            .filter { it.isValid && !it.propertyName.startsWith("-") }
            .map { declaration ->
                transformCssDeclareToUnoClass(
                    declaration = declaration,
                )
            }
    }
}