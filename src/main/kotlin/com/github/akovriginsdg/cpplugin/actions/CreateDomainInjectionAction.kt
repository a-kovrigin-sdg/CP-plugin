package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import java.util.*

class CreateDomainInjectionAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(CreateDomainInjectionAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || file == null || !file.isDirectory) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val relativePath = getPathRelativeToRoot(project, file)

        if (relativePath == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isInsideDomain = relativePath == PluginConst.DOMAIN_ROOT_DIR ||
                relativePath.startsWith("${PluginConst.DOMAIN_ROOT_DIR}/")
        val isInsideWeb = relativePath.startsWith(PluginConst.DATING_WEB_ROOT)
        val isInsideMobile = relativePath.startsWith(PluginConst.DATING_MOBILE_ROOT)

        e.presentation.isEnabledAndVisible = isInsideDomain && !isInsideWeb && !isInsideMobile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentDirectory = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val inputName = Messages.showInputDialog(
            project,
            "Enter name (PascalCase for Class, camelCase for Function):",
            "Create Domain Injection",
            Messages.getQuestionIcon()
        )

        if (inputName.isNullOrBlank()) return

        val projectRoot = project.guessProjectDir() ?: return

        generateDomainFiles(project, currentDirectory, inputName, projectRoot)
    }

    fun generateDomainFiles(
        project: Project,
        currentDirectory: VirtualFile,
        inputName: String,
        projectRoot: VirtualFile
    ) {
        val isFunction = inputName.first().isLowerCase()

        val camelName = inputName.replaceFirstChar { it.lowercase(Locale.getDefault()) }
        val pascalName = inputName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val kebabName = toKebabCase(pascalName)

        val tsFileName = "$kebabName.ts"
        val contractsFileName = "$kebabName.contracts.ts"

        val relativePathFromRoot = VfsUtil.getRelativePath(currentDirectory, projectRoot) ?: ""
        val subPath = relativePathFromRoot.removePrefix(PluginConst.DOMAIN_ROOT_DIR).removePrefix("/")

        val importPathSuffix = if (subPath.isNotEmpty()) "$subPath/$kebabName" else kebabName
        val contractImportPath = if (subPath.isNotEmpty()) "$subPath/$kebabName.contracts" else "$kebabName.contracts"

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val psiManager = PsiManager.getInstance(project)
                val currentPsiDir = psiManager.findDirectory(currentDirectory) ?: return@runWriteCommandAction

                var createdContractsFile: VirtualFile? = null
                var createdMainFile: VirtualFile? = null
                var createdWebFile: VirtualFile? = null
                var createdMobileFile: VirtualFile? = null

                if (currentPsiDir.findFile(contractsFileName) == null) {
                    val contractsFile = currentPsiDir.createFile(contractsFileName)
                    createdContractsFile = contractsFile.virtualFile

                    val contractsContent = if (isFunction) {
                        String.format(PluginConst.TPL_DOMAIN_CONTRACTS_FUNCTION, pascalName)
                    } else {
                        String.format(PluginConst.TPL_DOMAIN_CONTRACTS_CLASS, pascalName)
                    }

                    val document = PsiDocumentManager.getInstance(project).getDocument(contractsFile)
                    if (document != null) {
                        document.setText(contractsContent)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                }

                if (currentPsiDir.findFile(tsFileName) == null) {
                    val mainFile = currentPsiDir.createFile(tsFileName)
                    createdMainFile = mainFile.virtualFile

                    val mainContent = String.format(
                        PluginConst.TPL_DOMAIN_AGGREGATOR,
                        PluginConst.DOMAIN_IMPORT_PREFIX,
                        importPathSuffix,
                        kebabName
                    )

                    val document = PsiDocumentManager.getInstance(project).getDocument(mainFile)
                    if (document != null) {
                        document.setText(mainContent)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                }

                val webPathFull = "${PluginConst.WEB_DOMAIN_PATH}/$subPath".trimEnd('/')
                val mobilePathFull = "${PluginConst.MOBILE_DOMAIN_PATH}/$subPath".trimEnd('/')

                val targetContent = if (isFunction) {
                    String.format(
                        PluginConst.TPL_DOMAIN_INJECTION_TARGET_FUNCTION,
                        pascalName,
                        PluginConst.DOMAIN_IMPORT_PREFIX,
                        contractImportPath,
                        camelName, pascalName, camelName, camelName
                    )
                } else {
                    String.format(
                        PluginConst.TPL_DOMAIN_INJECTION_TARGET_CLASS,
                        pascalName,
                        PluginConst.DOMAIN_IMPORT_PREFIX,
                        contractImportPath,
                        pascalName, pascalName, pascalName, pascalName, pascalName
                    )
                }

                createdWebFile = createInjectionTargetFile(project, projectRoot, webPathFull, tsFileName, targetContent)
                createdMobileFile = createInjectionTargetFile(project, projectRoot, mobilePathFull, tsFileName, targetContent)

                if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
                    val fileEditorManager = FileEditorManager.getInstance(project)

                    val filesToOpen = listOfNotNull(createdContractsFile, createdWebFile, createdMobileFile)
                    filesToOpen.forEach { file ->
                        fileEditorManager.openFile(file, false) // focus = false
                    }

                    if (createdMainFile != null) {
                        fileEditorManager.openFile(createdMainFile!!, true) // focus = true
                    }
                }

            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Error creating files: ${ex.message}", "Error")
            }
        }
    }

    /**
     * Возвращает VirtualFile созданного (или найденного) файла
     */
    private fun createInjectionTargetFile(
        project: Project,
        root: VirtualFile,
        relativePath: String,
        fileName: String,
        fileContent: String
    ): VirtualFile? {
        val targetDir = VfsUtil.createDirectoryIfMissing(root, relativePath) ?: return null
        val psiManager = PsiManager.getInstance(project)
        val psiDir = psiManager.findDirectory(targetDir) ?: return null

        val existingFile = psiDir.findFile(fileName)
        if (existingFile == null) {
            val file = psiDir.createFile(fileName)
            val document = PsiDocumentManager.getInstance(project).getDocument(file)
            if (document != null) {
                document.setText(fileContent)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
            return file.virtualFile
        } else {
            return existingFile.virtualFile
        }
    }

    private fun getPathRelativeToRoot(project: Project, file: VirtualFile): String? {
        val baseDir = project.guessProjectDir() ?: return null
        return VfsUtil.getRelativePath(file, baseDir)
    }

    private fun toKebabCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z]+)"), "$1-$2").lowercase(Locale.getDefault())
    }
}