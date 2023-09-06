package me.rerere.unocssintellij.references

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.css.CssString
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssTokenImpl
import com.intellij.psi.filters.position.FilterPattern

class UnocssCssReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(CssString::class.java)
                .and(FilterPattern(UnocssCssThemeReferenceProvider.ReferenceFilter)),
            UnocssCssThemeReferenceProvider
        )

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(CssTokenImpl::class.java)
                .withElementType(CssElementTypes.CSS_IDENT)
                .and(FilterPattern(UnocssCssScreenReferenceProvider.ReferenceFilter)),
            UnocssCssScreenReferenceProvider
        )
    }
}
