package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

open class BaseTestFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        FileDocumentManager.getInstance().saveAllDocuments()

        val sourceFile: VirtualFile
        val testFile: VirtualFile?

        if (currentFile.nameSequence.endsWith(".test.ts")) {
            testFile = currentFile
            val sourceName = currentFile.name.replace(".test.ts", ".ts")
            sourceFile = currentFile.parent.findChild(sourceName) ?: run {
                Messages.showErrorDialog(project, "Could not find source file '$sourceName' in the same directory.", "Error")
                return
            }
        } else {
            sourceFile = currentFile
            val testName = "${currentFile.nameWithoutExtension}.test.ts"
            testFile = currentFile.parent.findChild(testName)
        }

        val psiManager = PsiManager.getInstance(project)
        val sourcePsiFile = psiManager.findFile(sourceFile) as? JSFile ?: return

        val importData = analyzeImports(sourcePsiFile, project)
        val sourceImportPath = calculateAbsoluteImportPath(sourceFile)

        if (sourceImportPath == null) {
            Messages.showErrorDialog(project, "Could not determine domain path for file: ${sourceFile.name}", "Error")
            return
        }

        val sourceExports = getExportedNames(sourcePsiFile)

        if (testFile != null) {
            val testPsiFile = psiManager.findFile(testFile) ?: return
            updateTestFile(project, testPsiFile, importData)
            FileEditorManager.getInstance(project).openFile(testFile, true)
        } else {
            val content = generateTestContent(sourceImportPath, sourceExports, importData)
            createTestFile(project, sourceFile.parent, sourceFile.nameWithoutExtension, content)
        }
    }

    data class DependencyInfo(
        val originalPath: String,
        val mockPath: String,
        val namedImports: List<String>
    )

    private fun analyzeImports(psiFile: JSFile, project: Project): List<DependencyInfo> {
        val result = mutableListOf<DependencyInfo>()
        val imports = PsiTreeUtil.findChildrenOfType(psiFile, ES6ImportDeclaration::class.java)

        for (imp in imports) {
            val isTypeDeclaration = imp.node.findChildByType(JSTokenTypes.TYPE_KEYWORD) != null
            if (isTypeDeclaration) continue

            val fromPath = imp.fromClause?.referenceText?.trim('\'', '"') ?: continue

            if (!fromPath.startsWith("@sdv/")) continue

            val specifiers = imp.importSpecifiers.filter { spec ->
                spec.node.findChildByType(JSTokenTypes.TYPE_KEYWORD) == null
            }.mapNotNull { it.name }

            if (specifiers.isEmpty()) continue

            val mockPath = resolveMockPath(imp, fromPath, project)
            result.add(DependencyInfo(fromPath, mockPath, specifiers))
        }

        return result
    }

    private fun resolveMockPath(importDecl: ES6ImportDeclaration, originalPath: String, project: Project): String {
        val resolvedElement = importDecl.fromClause?.references?.firstOrNull()?.resolve()

        if (resolvedElement is PsiDirectory) {
            return "$originalPath/index.mock"
        }

        if (resolvedElement is PsiFile) {
            return resolveFromFile(resolvedElement, originalPath)
        }

        val segments = originalPath.split("/")
        if (segments.size > 1) {
            val relativePath = segments.drop(1).joinToString("/")
            val roots = ProjectRootManager.getInstance(project).contentRoots

            for (root in roots) {
                val targetDir = root.findFileByRelativePath(relativePath)
                if (targetDir != null && targetDir.isDirectory) {
                    return "$originalPath/index.mock"
                }

                val targetFile = root.findFileByRelativePath("$relativePath.ts")
                if (targetFile != null && targetFile.exists()) {
                    val parent = targetFile.parent
                    if (parent != null) {
                        val nameNoExt = targetFile.nameWithoutExtension

                        if (parent.findChild("$nameNoExt.mock.ts") != null) {
                            return "$originalPath.mock"
                        }

                        val subDir = parent.findChild(nameNoExt)
                        if (subDir != null && subDir.isDirectory && subDir.findChild("index.mock.ts") != null) {
                            return "$originalPath/index.mock"
                        }
                    }
                    return "$originalPath.mock"
                }
            }
        }

        if (originalPath.endsWith("/index")) return "$originalPath.mock"
        return "$originalPath.mock"
    }

    private fun resolveFromFile(file: PsiFile, originalPath: String): String {
        val parentDir = file.parent ?: return "$originalPath.mock"
        val nameNoExt = file.virtualFile.nameWithoutExtension

        if (nameNoExt == "index") {
            return if (originalPath.endsWith("/index")) "$originalPath.mock" else "$originalPath/index.mock"
        }

        if (parentDir.findFile("$nameNoExt.mock.ts") != null) {
            return "$originalPath.mock"
        }

        val subDir = parentDir.findSubdirectory(nameNoExt)
        if (subDir != null && subDir.findFile("index.mock.ts") != null) {
            return "$originalPath/index.mock"
        }

        return "$originalPath.mock"
    }

    private fun getExportedNames(psiFile: JSFile): List<String> {
        val names = mutableListOf<String>()
        val children = psiFile.children

        for (element in children) {
            if (element is JSAttributeListOwner) {
                val attrs = element.attributeList
                if (attrs != null && attrs.hasModifier(JSAttributeList.ModifierType.EXPORT)) {
                    when (element) {
                        is JSClass -> element.name?.let { names.add(it) }
                        is JSFunction -> element.name?.let { names.add(it) }
                        is JSVarStatement -> element.variables.forEach { variable ->
                            variable.name?.let { names.add(it) }
                        }
                    }
                }
            }
            if (element is ES6ExportDeclaration) {
                val specifiers = element.exportSpecifiers
                if (specifiers.isNotEmpty()) {
                    specifiers.forEach { spec ->
                        val exportedName = spec.alias?.name ?: spec.referenceName
                        if (exportedName != null && exportedName != "default") {
                            names.add(exportedName)
                        }
                    }
                }
            }
        }
        return names
    }

    private fun generateTestContent(sourcePath: String, sourceExports: List<String>, dependencies: List<DependencyInfo>): String {
        val sb = StringBuilder()

        if (sourceExports.isNotEmpty()) {
            sb.append("import { ${sourceExports.joinToString(", ")} } from '$sourcePath'\n\n")
        } else {
            sb.append("// TODO: Import tested class from '$sourcePath'\n\n")
        }

        dependencies.forEach { dep ->
            sb.append("jest.useExtendedMock('${dep.mockPath}')\n")
        }
        if (dependencies.isNotEmpty()) sb.append("\n")

        dependencies.forEach { dep ->
            val names = dep.namedImports.joinToString(", ")
            sb.append("import { $names } from '${dep.mockPath}'\n")
        }

        return sb.toString()
    }

    private fun updateTestFile(project: Project, testFile: PsiFile, dependencies: List<DependencyInfo>) {
        WriteCommandAction.runWriteCommandAction(project) {
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = documentManager.getDocument(testFile) ?: return@runWriteCommandAction
            documentManager.commitDocument(document)

            val text = document.text
            val newMocks = StringBuilder()

            dependencies.forEach { dep ->
                if (!text.contains("jest.useExtendedMock('${dep.mockPath}')")) {
                    newMocks.append("jest.useExtendedMock('${dep.mockPath}')\n")
                }
            }

            if (newMocks.isNotEmpty()) {
                val lastMockIndex = text.lastIndexOf("jest.useExtendedMock")
                if (lastMockIndex != -1) {
                    val endOfLine = text.indexOf('\n', lastMockIndex)
                    if (endOfLine != -1) {
                        document.insertString(endOfLine + 1, newMocks.toString())
                    }
                } else {
                    val firstImport = text.indexOf("import {")
                    if (firstImport != -1) {
                        val endOfImport = text.indexOf('\n', text.indexOf("from", firstImport))
                        if (endOfImport != -1) {
                            document.insertString(endOfImport + 1, "\n" + newMocks.toString())
                        } else {
                            document.insertString(0, newMocks.toString())
                        }
                    } else {
                        document.insertString(0, newMocks.toString())
                    }
                }
            }
        }
    }

    private fun calculateAbsoluteImportPath(file: VirtualFile): String? {
        val pathParts = file.path.split("/")
        val domainIndex = pathParts.indexOf(PluginConst.DOMAIN_ROOT_DIR)
        if (domainIndex == -1) return null
        val relativePath = pathParts.drop(domainIndex + 1).joinToString("/")
        var pathNoExt = relativePath.removeSuffix(".ts")
        if (pathNoExt.endsWith("/index")) pathNoExt = pathNoExt.removeSuffix("/index")
        return "${PluginConst.DOMAIN_IMPORT_PREFIX}/$pathNoExt"
    }

    private fun createTestFile(
        project: Project,
        directory: VirtualFile,
        fileNameWithoutExt: String,
        content: String
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val psiDirectory = PsiManager.getInstance(project).findDirectory(directory) ?: return@runWriteCommandAction
                val fileName = "$fileNameWithoutExt.test.ts"

                val existing = psiDirectory.findFile(fileName)
                existing?.delete()

                val newFile = psiDirectory.createFile(fileName)
                val document = PsiDocumentManager.getInstance(project).getDocument(newFile)
                document?.setText(content)

                FileEditorManager.getInstance(project).openFile(newFile.virtualFile, true)

            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Error creating test: ${e.message}", "Error")
            }
        }
    }
}