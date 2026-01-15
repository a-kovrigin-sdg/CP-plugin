package com.github.akovriginsdg.cpplugin.actions.offeringTemplateGenerator

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
import java.util.Properties

data class OfferingInputData(
    val className: String,
    val useShortFileName: Boolean
)

open class CreateOfferingAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || file == null || !file.isDirectory) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val path = file.path
        e.presentation.isEnabledAndVisible = path.endsWith("/${PluginConst.DOMAIN_ROOT_DIR}") ||
                path.contains("/${PluginConst.DOMAIN_ROOT_DIR}/")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile) ?: return

        val inputData = getUserInput(project) ?: return
        val inputName = inputData.className
        if (inputName.isBlank()) return

        val className = normalizeClassName(inputName)
        val baseName = className.removeSuffix("Offering")
        val offerName = "${baseName}Offer"

        val fileNameWithoutExtension = if (inputData.useShortFileName) "offering" else toKebabCase(className)

        val checkFileName = "$fileNameWithoutExtension.ts"
        if (psiDirectory.findFile(checkFileName) != null) {
            Messages.showErrorDialog(project, "File $checkFileName already exists.", "Error")
            return
        }

        val template = findTemplate(project)
        if (template == null) {
            Messages.showErrorDialog(project, "Template not found.", "Error")
            return
        }

        createOfferingFile(project, psiDirectory, template, fileNameWithoutExtension, className, offerName)
    }

    protected open fun getUserInput(project: Project): OfferingInputData? {
        val dialog = CreateOfferingDialog(project)
        if (!dialog.showAndGet()) return null
        return OfferingInputData(dialog.nameField.text, dialog.shortNameCheckbox.isSelected)
    }

    protected open fun findTemplate(project: Project): FileTemplate? {
        val manager = FileTemplateManager.getInstance(project)
        return try {
            manager.getJ2eeTemplate(PluginConst.TEMPLATE_OFFERING)
        } catch (e: Exception) {
            try {
                manager.getTemplate(PluginConst.TEMPLATE_OFFERING)
            } catch (ignored: Exception) {
                null
            }
        }
    }

    protected open fun createOfferingFile(
        project: Project,
        directory: PsiDirectory,
        template: FileTemplate,
        fileNameWithoutExtension: String,
        className: String,
        offerName: String
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val props = Properties(FileTemplateManager.getInstance(project).defaultProperties)
                props.setProperty("CLASS_NAME", className)
                props.setProperty("OFFER_NAME", offerName)

                val createdElement = FileTemplateUtil.createFromTemplate(
                    template,
                    fileNameWithoutExtension,
                    props,
                    directory
                )

                if (createdElement is PsiFile) {
                    FileEditorManager.getInstance(project).openFile(createdElement.virtualFile, true)
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Could not create file: ${e.message}", "Error")
            }
        }
    }

    private fun normalizeClassName(input: String): String {
        var name = input.trim().replaceFirstChar { it.uppercase() }
        if (!name.endsWith("Offering")) name += "Offering"
        return name
    }

    private fun toKebabCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z]+)"), "$1-$2").lowercase()
    }
}