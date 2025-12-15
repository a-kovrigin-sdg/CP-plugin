package com.github.akovriginsdg.cpplugin.l10nReferenceResolver

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import com.github.akovriginsdg.cpplugin.PluginUtils

class L10nReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), object : PsiReferenceProvider() {
            override fun getReferencesByElement(
                element: PsiElement,
                context: ProcessingContext
            ): Array<PsiReference> {
                if (!PluginUtils.isTargetProject(element.project)) {
                    return PsiReference.EMPTY_ARRAY
                }

                // СЦЕНАРИЙ 1: JSX Атрибут href="..."
                if (element is XmlAttributeValue) {
                    return handleXmlAttribute(element)
                }

                // СЦЕНАРИЙ 2: JS Строка (href={'...'} или useLocalizedString('...'))
                if (element is JSLiteralExpression && element.isStringLiteral) {
                    return handleJsLiteral(element)
                }

                return PsiReference.EMPTY_ARRAY
            }
        })
    }

    private fun handleXmlAttribute(element: XmlAttributeValue): Array<PsiReference> {
        val attribute = element.parent as? XmlAttribute ?: return PsiReference.EMPTY_ARRAY
        if (attribute.name != "href") return PsiReference.EMPTY_ARRAY

        val tag = attribute.parent as? XmlTag ?: return PsiReference.EMPTY_ARRAY
        val tagName = tag.localName

        if (tagName != "LocalizedText" && tagName != "LocalizedString") {
            return PsiReference.EMPTY_ARRAY
        }

        val range = TextRange(1, element.textLength - 1)
        return arrayOf(L10nReference(element, range, element.value))
    }

    private fun handleJsLiteral(element: JSLiteralExpression): Array<PsiReference> {
        val value = element.stringValue ?: return PsiReference.EMPTY_ARRAY
        val range = TextRange(1, element.textLength - 1)

        // --- ВАРИАНТ А: href={'path'} ---
        val xmlAttribute = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java)
        if (xmlAttribute != null && xmlAttribute.name == "href") {
            val tag = xmlAttribute.parent as? XmlTag
            val tagName = tag?.localName

            if (tagName == "LocalizedText" || tagName == "LocalizedString") {
                return arrayOf(L10nReference(element, range, value))
            }
        }

        // --- ВАРИАНТ Б: useLocalizedString('path') ---
        val argumentList = element.parent as? com.intellij.lang.javascript.psi.JSArgumentList
        if (argumentList != null) {
            val callExpression = argumentList.parent as? JSCallExpression
            val methodExpr = callExpression?.methodExpression as? JSReferenceExpression

            if (methodExpr?.referenceName == "useLocalizedString") {
                return arrayOf(L10nReference(element, range, value))
            }
        }

        return PsiReference.EMPTY_ARRAY
    }
}