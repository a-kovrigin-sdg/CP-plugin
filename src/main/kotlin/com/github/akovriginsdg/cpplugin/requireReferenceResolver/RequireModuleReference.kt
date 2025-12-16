package com.github.akovriginsdg.cpplugin.requireReferenceResolver

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.guessProjectDir
import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.psi.*

class RequireModuleReference(
    element: PsiElement,
    textRange: TextRange,
    private val path: String
) : PsiReferenceBase<PsiElement>(element, textRange) {

    private val appBaseRelPath = PluginConst.APP_ROOT

    override fun resolve(): PsiElement? {
        val project = element.project
        val baseDir = project.guessProjectDir() ?: return null
        val psiManager = PsiManager.getInstance(project)

        val cleanPath = path.removePrefix("/")

        val targetBasePath = "$appBaseRelPath/$cleanPath"

        val candidates = listOf(
            "$targetBasePath.js",
            "$targetBasePath/index.js"
        )

        for (candidate in candidates) {
            val vFile = baseDir.findFileByRelativePath(candidate)
            if (vFile != null && !vFile.isDirectory) {
                return psiManager.findFile(vFile)
            }
        }

        val vDir = baseDir.findFileByRelativePath(targetBasePath)
        if (vDir != null && vDir.isDirectory) {
            return psiManager.findDirectory(vDir)
        }

        return null
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}