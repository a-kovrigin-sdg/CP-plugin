package com.github.akovriginsdg.cpplugin.fluxReferenceResolver

import com.intellij.lang.javascript.psi.*
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class FluxComponentReference(
    element: PsiElement,
    textRange: TextRange
) : PsiReferenceBase<PsiElement>(element, textRange) {

    private val configRelPath = "dating-web/orbit/source/modules/integrator/components/config.js"
    private val importBaseRelPath = "dating-web/orbit/source"

    private val supportedExtensions = listOf(
        "", ".tsx", ".ts", ".jsx", ".js",
        "/index.tsx", "/index.ts", "/index.jsx", "/index.js"
    )

    override fun resolve(): PsiElement? {
        val project = element.project
        val basePath = project.basePath ?: return null

        val configPath = "$basePath/$configRelPath"
        val configFileVirtual = LocalFileSystem.getInstance().findFileByPath(configPath) ?: return null
        val configFile = PsiManager.getInstance(project).findFile(configFileVirtual) as? JSFile ?: return null
        val configObject = findComponentsConfigObject(configFile) ?: return null

        val keyName = element.text.trim('"', '\'')
        val property = configObject.findProperty(keyName) ?: return null

        val value = property.value as? JSFunction ?: return null
        val returnExpression = getReturnExpression(value) ?: return null

        return resolveTargetFile(returnExpression, basePath)
    }

    private fun findComponentsConfigObject(file: JSFile): JSObjectLiteralExpression? {
        val vars = PsiTreeUtil.findChildrenOfType(file, JSVariable::class.java)
        return vars.firstOrNull { it.name == "componentsConfig" }?.initializer as? JSObjectLiteralExpression
    }

    private fun getReturnExpression(function: JSFunction): JSExpression? {
        if (function is JSFunctionExpression) {
            val children = function.children
            for (child in children) {
                if (child is JSExpression && child !is JSParameterList) return child
            }
        }
        val block = function.block
        if (block != null) {
            val returnStatement = PsiTreeUtil.findChildOfType(block, JSReturnStatement::class.java)
            return returnStatement?.expression
        }
        return PsiTreeUtil.findChildOfType(function, JSExpression::class.java)
    }

    private fun resolveTargetFile(expression: JSExpression, projectBasePath: String): PsiElement? {
        // --- SCENARIO 1: Lazy Import ---
        val lazyImportPath = findPathInImportCallRecursive(expression)
        if (lazyImportPath != null) {
            return findFileByCustomPath(lazyImportPath, projectBasePath)
        }

        // --- SCENARIO 2: Direct Reference ---
        if (expression is JSReferenceExpression) {
            val resolvedElement = expression.resolve() ?: return null

            val importPath = findPathFromParentImport(resolvedElement)
            if (importPath != null) {
                val result = findFileByCustomPath(importPath, projectBasePath)
                if (result != null) return result
            }
            return resolvedElement
        }
        return null
    }

    private fun findPathInImportCallRecursive(root: PsiElement): String? {
        var foundPath: String? = null
        root.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (foundPath != null) return
                if (element is JSExpression) {
                    val text = extractStringValue(element)
                    if (text != null) {
                        val parent = element.parent
                        if (parent != null) {
                            val parentText = parent.text.trim()
                            if (parentText.startsWith("import(") && parentText.contains(element.text)) {
                                foundPath = text
                                return
                            }
                        }
                    }
                }
                super.visitElement(element)
            }
        })
        return foundPath
    }

    private fun findPathFromParentImport(element: PsiElement): String? {
        var parent = element.parent

        while (parent != null && parent !is PsiFile) {
            val text = parent.text.trim()

            if (text.startsWith("import") && text.contains("from")) {
                val stringCandidates = mutableListOf<String>()

                parent.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(e: PsiElement) {
                        val rawText = e.text.trim()
                        if (isQuotedString(rawText)) {
                            stringCandidates.add(rawText.substring(1, rawText.length - 1))
                        }
                        super.visitElement(e)
                    }
                })

                if (stringCandidates.isNotEmpty()) {
                    return stringCandidates.last()
                }
            }
            parent = parent.parent
        }
        return null
    }

    private fun isQuotedString(text: String): Boolean {
        if (text.length < 2) return false
        if (text.startsWith("'") && text.endsWith("'")) return true
        if (text.startsWith("\"") && text.endsWith("\"")) return true
        if (text.startsWith("`") && text.endsWith("`")) return true
        return false
    }

    private fun extractStringValue(expression: JSExpression): String? {
        if (expression is JSLiteralExpression && expression.isStringLiteral) {
            return expression.stringValue
        }
        val text = expression.text.trim()
        if (isQuotedString(text)) return text.substring(1, text.length - 1)
        return null
    }

    private fun findFileByCustomPath(path: String, projectBasePath: String): PsiElement? {
        val fs = LocalFileSystem.getInstance()
        val psiManager = PsiManager.getInstance(element.project)

        val cleanPath = path.removePrefix("/")
        val pathCandidates = mutableListOf<String>()

        pathCandidates.add("$projectBasePath/$cleanPath")
        if (!cleanPath.startsWith(importBaseRelPath)) {
            pathCandidates.add("$projectBasePath/$importBaseRelPath/$cleanPath")
        }

        // 1. Ищем файл
        for (basePathCandidate in pathCandidates) {
            for (ext in supportedExtensions) {
                val fullPath = basePathCandidate + ext
                val vFile = fs.findFileByPath(fullPath)
                if (vFile != null && !vFile.isDirectory) {
                    return psiManager.findFile(vFile)
                }
            }
        }

        // 2. Ищем папку (Fallback)
        for (basePathCandidate in pathCandidates) {
            val vFile = fs.findFileByPath(basePathCandidate)
            if (vFile != null && vFile.isDirectory) {
                return psiManager.findDirectory(vFile)
            }
        }

        return null
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}
