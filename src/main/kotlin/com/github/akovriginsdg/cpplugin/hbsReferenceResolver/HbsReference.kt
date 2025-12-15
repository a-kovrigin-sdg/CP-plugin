package com.github.akovriginsdg.cpplugin.hbsReferenceResolver

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*

class HbsReference(
    element: PsiElement,
    textRange: TextRange,
    private val fileCandidates: List<String>,
    private val fallbackPath: String? = null
) : PsiReferenceBase<PsiElement>(element, textRange) {

    override fun resolve(): PsiElement? {
        val fs = LocalFileSystem.getInstance()
        val project = element.project
        val psiManager = PsiManager.getInstance(project)

        for (path in fileCandidates) {
            val file = fs.findFileByPath(path)
            // Если файл найден и это НЕ директория — возвращаем его
            if (file != null && !file.isDirectory) {
                return psiManager.findFile(file)
            }
        }

        if (fallbackPath != null) {
            val dir = fs.findFileByPath(fallbackPath)
            if (dir != null && dir.isDirectory) {
                return psiManager.findDirectory(dir)
            }
        }

        return null
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}