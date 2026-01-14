package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.*

open class CreateComponentAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val isVisible = project != null &&
                selectedFile != null &&
                selectedFile.isDirectory &&
                selectedFile.path.replace("\\", "/").contains(PluginConst.WEB_COMPONENTS_FOLDER)

        e.presentation.isEnabledAndVisible = isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFolder = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val psiManager = PsiManager.getInstance(project)
        val parentPsiDir = psiManager.findDirectory(selectedFolder) ?: return

        val componentName = getUserInput(project) ?: return
        if (componentName.isBlank()) return

        val folderName = toKebabCase(componentName)

        if (parentPsiDir.findSubdirectory(folderName) != null) {
            Messages.showErrorDialog(project, "Directory $folderName already exists", "Error")
            return
        }

        val tplIndex = getTemplate(project, PluginConst.TPL_REACT_INDEX)
        val tplView = getTemplate(project, PluginConst.TPL_REACT_VIEW)
        val tplStyle = getTemplate(project, PluginConst.TPL_REACT_STYLE)

        if (tplIndex == null || tplView == null || tplStyle == null) {
            Messages.showErrorDialog(project, "Templates not found. Check Settings.", "Error")
            return
        }

        runWriteCreation(project, parentPsiDir, folderName, componentName, tplIndex, tplView, tplStyle)
    }

    protected open fun getUserInput(project: Project): String? {
        return Messages.showInputDialog(
            project,
            "Enter component name (e.g. UserProfile):",
            "Create New Component",
            Messages.getQuestionIcon()
        )
    }

    protected open fun getTemplate(project: Project, name: String): FileTemplate? {
        val manager = FileTemplateManager.getInstance(project)
        return try {
            manager.getJ2eeTemplate(name)
        } catch (e: Exception) {
            try { manager.getTemplate(name) } catch (ignored: Exception) { null }
        }
    }

    protected open fun runWriteCreation(
        project: Project,
        parentDir: PsiDirectory,
        folderName: String,
        componentName: String,
        tplIndex: FileTemplate,
        tplView: FileTemplate,
        tplStyle: FileTemplate
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val componentDir = parentDir.createSubdirectory(folderName)

                val props = Properties(FileTemplateManager.getInstance(project).defaultProperties)
                props.setProperty("COMPONENT_NAME", componentName)

                val indexFile = FileTemplateUtil.createFromTemplate(tplIndex, "index", props, componentDir)

                FileTemplateUtil.createFromTemplate(tplView, "view", props, componentDir)

                FileTemplateUtil.createFromTemplate(tplStyle, "styles.module", props, componentDir)

                if (indexFile is PsiFile) {
                    FileEditorManager.getInstance(project).openFile(indexFile.virtualFile, true)
                }

            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Error creating component: ${ex.message}", "Error")
            }
        }
    }

    private fun toKebabCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z]+)"), "$1-$2").lowercase(Locale.getDefault())
    }
}