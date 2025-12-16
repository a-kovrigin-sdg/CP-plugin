package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.github.akovriginsdg.cpplugin.PluginUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager

class CreateComponentAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val isVisible = project != null &&
                PluginUtils.isTargetProject(project) &&
                selectedFile != null &&
                selectedFile.isDirectory

        e.presentation.isEnabledAndVisible = isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFolder = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val componentName = Messages.showInputDialog(
            project,
            "Enter component name (e.g. UserProfile):",
            "Create New Component",
            Messages.getQuestionIcon()
        )

        if (componentName.isNullOrBlank()) return

        executeCommand(project, "Create Component") {
            runWriteAction {
                try {
                    createComponentStructure(project, selectedFolder, componentName)
                } catch (ex: Exception) {
                    Messages.showErrorDialog(project, "Error creating component: ${ex.message}", "Error")
                }
            }
        }
    }

    private fun createComponentStructure(project: Project, parentDir: VirtualFile, name: String) {
        val psiManager = PsiManager.getInstance(project)
        val parentPsiDir = psiManager.findDirectory(parentDir) ?: return

        val folderName = PluginUtils.toKebabCase(name) // UserProfile -> user-profile

        val existingDir = parentPsiDir.findSubdirectory(folderName)
        if (existingDir != null) {
            throw Exception("Directory $folderName already exists")
        }

        val componentDir = parentPsiDir.createSubdirectory(folderName)

        createFile(componentDir, "index.tsx", PluginConst.TPL_INDEX.format(name, name))

        createFile(componentDir, "view.tsx", PluginConst.TPL_VIEW)

        createFile(componentDir, "styles.module.less", PluginConst.TPL_STYLE)

        // Открываем index.tsx (или view.tsx, как удобнее)
        val fileToOpen = componentDir.findFile("index.tsx")?.virtualFile
        if (fileToOpen != null) {
            FileEditorManager.getInstance(project).openFile(fileToOpen, true)
        }
    }

    private fun createFile(dir: PsiDirectory, fileName: String, content: String): VirtualFile? {
        val fileFactory = PsiFileFactory.getInstance(dir.project)
        val file = fileFactory.createFileFromText(fileName, content)
        val addedElement = dir.add(file)

        return (addedElement as? com.intellij.psi.PsiFile)?.virtualFile
    }
}