package com.github.akovriginsdg.cpplugin.hbsReferenceResolver

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

class HbsReference(
    element: PsiElement,
    textRange: TextRange,
    private val fileCandidates: List<String>,
    private val fallbackPath: String? = null
) : PsiReferenceBase<PsiElement>(element, textRange) {

    override fun resolve(): PsiElement? {
        val project = element.project
        // 1. Получаем виртуальный корень проекта
        val baseDir = project.guessProjectDir() ?: return null
        val basePath = project.basePath ?: return null
        val psiManager = PsiManager.getInstance(project)

        // Вспомогательная функция: превращает абсолютный путь в относительный
        fun getRelativePath(path: String): String {
            // Если путь начинается с корня проекта (например /mock/path/dating-web/...), отрезаем корень
            return if (path.startsWith(basePath)) {
                path.substring(basePath.length).removePrefix("/")
            } else {
                // Если путь уже был относительным или "кривым", просто убираем начальный слеш
                path.removePrefix("/")
            }
        }

        // 2. Проходим по кандидатам
        for (path in fileCandidates) {
            val relativePath = getRelativePath(path)
            val vFile = baseDir.findFileByRelativePath(relativePath)

            if (vFile != null && !vFile.isDirectory) {
                return psiManager.findFile(vFile)
            }
        }

        // 3. Проверяем Fallback (папку)
        if (fallbackPath != null) {
            val relativeFallback = getRelativePath(fallbackPath)
            val vDir = baseDir.findFileByRelativePath(relativeFallback)

            if (vDir != null && vDir.isDirectory) {
                return psiManager.findDirectory(vDir)
            }
        }

        return null
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}