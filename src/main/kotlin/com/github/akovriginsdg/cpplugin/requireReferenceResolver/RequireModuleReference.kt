package com.github.akovriginsdg.cpplugin.requireReferenceResolver

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*

class RequireModuleReference(
    element: PsiElement,
    textRange: TextRange,
    private val path: String
) : PsiReferenceBase<PsiElement>(element, textRange) {

    private val appBaseRelPath = "dating-web/public/app"

    override fun resolve(): PsiElement? {
        val project = element.project
        val basePath = project.basePath ?: return null

        val cleanPath = path.removePrefix("/")

        val targetBasePath = "$basePath/$appBaseRelPath/$cleanPath"

        val fs = LocalFileSystem.getInstance()
        val psiManager = PsiManager.getInstance(project)

        val candidates = listOf(
            "$targetBasePath.js",       // appearance.js
            "$targetBasePath/index.js"  // appearance/index.js
        )

        for (candidate in candidates) {
            val vFile = fs.findFileByPath(candidate)
            if (vFile != null && !vFile.isDirectory) {
                return psiManager.findFile(vFile)
            }
        }

        // Фоллбэк: если js файлы не найдены, попробуем открыть просто папку
        val vDir = fs.findFileByPath(targetBasePath)
        if (vDir != null && vDir.isDirectory) {
            return psiManager.findDirectory(vDir)
        }

        return null
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}