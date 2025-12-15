package com.github.akovriginsdg.cpplugin.fluxReferenceResolver

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.github.akovriginsdg.cpplugin.PluginUtils

class FluxComponentReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {

        // Используем универсальный паттерн
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

                // 2. Проверяем, что файл находится в папке /widgets/ (оптимизация)
                val filePath = element.containingFile.virtualFile?.path ?: ""
                if (!filePath.contains("/dating-web/public/app/widgets/")) {
                    return PsiReference.EMPTY_ARRAY
                }

                val argumentList = element.parent as? com.intellij.lang.javascript.psi.JSArgumentList ?: return PsiReference.EMPTY_ARRAY
                val args = argumentList.arguments

                if (args.size < 2 || args[1] != element) {
                    return PsiReference.EMPTY_ARRAY
                }

                val firstArg = args[0] as? JSLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                if (firstArg.stringValue != "command.flux.component.render") {
                    return PsiReference.EMPTY_ARRAY
                }

                val callExpression = argumentList.parent as? JSCallExpression ?: return PsiReference.EMPTY_ARRAY
                val methodExpression = callExpression.methodExpression as? JSReferenceExpression ?: return PsiReference.EMPTY_ARRAY

                if (methodExpression.referenceName != "emit") {
                    return PsiReference.EMPTY_ARRAY
                }

                val qualifier = methodExpression.qualifier

                if (qualifier == null || qualifier.text != "bus") {
                    return PsiReference.EMPTY_ARRAY
                }

                return arrayOf(
                    FluxComponentReference(
                        element,
                        TextRange(1, element.textLength - 1)
                    )
                )
            }
        })
    }
}
