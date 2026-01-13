package com.github.akovriginsdg.cpplugin.requireReferenceResolver

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.github.akovriginsdg.cpplugin.PluginUtils

class RequireModuleReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), object : PsiReferenceProvider() {
            override fun getReferencesByElement(
                element: PsiElement,
                context: ProcessingContext
            ): Array<PsiReference> {
                if (!PluginUtils.isTargetProject(element.project)) {
                    return PsiReference.EMPTY_ARRAY
                }

                if (element !is JSLiteralExpression || !element.isStringLiteral) {
                    return PsiReference.EMPTY_ARRAY
                }

                val virtualFile = element.containingFile.virtualFile
                if (virtualFile == null || !virtualFile.path.contains("dating-web/public/app/")) {
                    return PsiReference.EMPTY_ARRAY
                }

                // Проверяем структуру: require('string')
                // Parent -> ArgumentList
                val argumentList = element.parent as? com.intellij.lang.javascript.psi.JSArgumentList ?: return PsiReference.EMPTY_ARRAY

                if (argumentList.arguments.isEmpty() || argumentList.arguments[0] != element) {
                    return PsiReference.EMPTY_ARRAY
                }

                // Parent -> CallExpression
                val callExpression = argumentList.parent as? JSCallExpression ?: return PsiReference.EMPTY_ARRAY
                val methodExpression = callExpression.methodExpression as? JSReferenceExpression ?: return PsiReference.EMPTY_ARRAY

                // Имя функции должно быть 'require'
                if (methodExpression.referenceName != "require") {
                    return PsiReference.EMPTY_ARRAY
                }

                // 4. Создаем ссылку
                val value = element.stringValue ?: return PsiReference.EMPTY_ARRAY

                val startOffset = 1
                val endOffset = element.textLength - 1

                if (startOffset > endOffset) {
                    return PsiReference.EMPTY_ARRAY
                }

                val range = TextRange(startOffset, endOffset)

                return arrayOf(RequireModuleReference(element, range, value))
            }
        })
    }
}