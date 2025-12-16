package com.github.akovriginsdg.cpplugin.usageSearcher

import com.github.akovriginsdg.cpplugin.PluginUtils
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

class ProjectFileUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val elementToSearch = queryParameters.elementToSearch

        if (elementToSearch !is PsiFile) return
        if (!PluginUtils.isTargetProject(elementToSearch.project)) return

        val modulePath = ReadAction.compute<String?, RuntimeException> {
            PluginUtils.getModulePath(elementToSearch)
        } ?: return

        if (modulePath.length < 2) return

        val scope = queryParameters.effectiveSearchScope
        val searchHelper = PsiSearchHelper.getInstance(elementToSearch.project)

        val searchWord = modulePath.substringAfterLast('/')

        if (searchWord.isEmpty()) return

        searchHelper.processElementsWithWord(
            { element: PsiElement, offsetInElement: Int ->
                val references = ReadAction.compute<Array<PsiReference>, RuntimeException> { element.references }

                for (ref in references) {
                    if (ref.isReferenceTo(elementToSearch)) {
                        if (!consumer.process(ref)) {
                            return@processElementsWithWord false
                        }
                    }
                }
                true
            },
            scope,
            searchWord,
            UsageSearchContext.IN_STRINGS,
            true
        )
    }
}