package com.github.akovriginsdg.cpplugin.l10nReferenceResolver

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.*

class L10nReference(
    element: PsiElement,
    textRange: TextRange,
    private val fullPath: String
) : PsiReferenceBase<PsiElement>(element, textRange) {

    override fun resolve(): PsiElement? {
        val project = element.project

        val baseDir = project.guessProjectDir() ?: return null

        val pathPart = fullPath.substringBefore('#')
        val cleanPath = pathPart.removePrefix("/")

        val relativePath = "l10n/$cleanPath/en-US.json"
        val virtualFile = baseDir.findFileByRelativePath(relativePath) ?: return null

        return PsiManager.getInstance(project).findFile(virtualFile)
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}