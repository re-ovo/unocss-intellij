package me.rerere.unocssintellij.inject

import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType

class CssInjector : LanguageInjectionContributor{
    override fun getInjection(context: PsiElement): Injection? {
        //println("inject $context | ${context.elementType} | ${context.text}")
        if(context.elementType == XmlElementType.XML_ATTRIBUTE_VALUE) {

        }
        return null
    }
}

