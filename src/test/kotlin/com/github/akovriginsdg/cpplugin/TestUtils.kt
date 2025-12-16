package com.github.akovriginsdg.cpplugin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

object TestUtils {
    fun debugPsi(fixture: CodeInsightTestFixture) {
        val file = fixture.file
        println("=== DEBUG PSI TREE START ===")
        println("File: ${file.name} | Type: ${file.fileType.name} | Class: ${file::class.java.simpleName}")

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val indent = "  ".repeat(getDepth(element))
                println("$indent- ${element::class.java.simpleName} : '${element.text.take(20)}'")
                super.visitElement(element)
            }
        })
        println("=== DEBUG PSI TREE END ===")

        // Debug элемента под курсором
        val elementAtCaret = file.findElementAt(fixture.caretOffset)
        println("Element at caret: ${elementAtCaret} (${elementAtCaret?.javaClass?.simpleName})")
        println("Parent: ${elementAtCaret?.parent} (${elementAtCaret?.parent?.javaClass?.simpleName})")
    }

    private fun getDepth(element: PsiElement): Int {
        var depth = 0
        var parent = element.parent
        while (parent != null) {
            depth++
            parent = parent.parent
        }
        return depth
    }
}