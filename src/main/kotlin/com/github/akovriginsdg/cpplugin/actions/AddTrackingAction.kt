package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.github.akovriginsdg.cpplugin.PluginUtils
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import java.util.*

open class AddTrackingAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || selectedFile == null || !selectedFile.isValid) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        if (!PluginUtils.isTargetProject(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val dir = if (selectedFile.isDirectory) selectedFile else selectedFile.parent
        if (dir == null || !dir.isValid) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val indexFile = dir.findChild("index.tsx")
        val trackingFile = dir.findChild("tracking.tsx")

        val hasIndex = indexFile != null && indexFile.isValid
        val noTracking = trackingFile == null || !trackingFile.isValid

        e.presentation.isEnabledAndVisible = hasIndex && noTracking
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val dir = if (selectedFile.isDirectory) selectedFile else selectedFile.parent ?: return
        val indexFile = dir.findChild("index.tsx") ?: return

        val psiManager = PsiManager.getInstance(project)
        val psiDir = psiManager.findDirectory(dir) ?: return

        val trackingTemplate = getTemplate(project, PluginConst.TPL_TRACKING)
        if (trackingTemplate == null) {
            Messages.showErrorDialog(project, "Template not found", "Error")
            return
        }

        runWriteModification(project, psiDir, indexFile, trackingTemplate)
    }

    /**
     * Основной метод изменения (открыт для тестов)
     */
    protected open fun runWriteModification(
        project: Project,
        dir: PsiDirectory,
        indexFile: VirtualFile,
        template: FileTemplate
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                createTrackingFile(project, dir, template)

                val document = FileDocumentManager.getInstance().getDocument(indexFile)
                if (document != null) {
                    val originalText = document.text
                    val newText = modifyIndexContent(originalText)

                    if (newText != originalText) {
                        document.setText(newText)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }

                    FileEditorManager.getInstance(project).openFile(indexFile, true)
                }

            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Error adding tracking: ${ex.message}", "Error")
            }
        }
    }

    protected open fun getTemplate(project: Project, name: String): FileTemplate? {
        val manager = FileTemplateManager.getInstance(project)
        return try {
            manager.getJ2eeTemplate(name)
        } catch (e: Exception) {
            try { manager.getTemplate(name) } catch (ignored: Exception) { null }
        }
    }

    private fun createTrackingFile(project: Project, dir: PsiDirectory, template: FileTemplate) {
        val props = Properties(FileTemplateManager.getInstance(project).defaultProperties)
        props.setProperty("COMPONENT_NAME", "TrackingWrapper")

        FileTemplateUtil.createFromTemplate(template, "tracking", props, dir)
    }

    fun modifyIndexContent(text: String): String {
        if (text.contains("import withTracking")) return text

        var currentText = text

        val importRegex = Regex("^import\\s+.*", RegexOption.MULTILINE)
        val imports = importRegex.findAll(text).toList()

        val importStatement = "import withTracking from './tracking'"

        if (imports.isNotEmpty()) {
            val lastImport = imports.last()
            val insertIndex = lastImport.range.last + 1
            currentText = currentText.substring(0, insertIndex) + "\n" + importStatement + currentText.substring(insertIndex)
        } else {
            currentText = "$importStatement\n\n$currentText"
        }

        val exportRegex = Regex("^(\\s*)export const\\s+(\\w+)\\s*=", RegexOption.MULTILINE)
        val exportMatch = exportRegex.find(currentText)

        if (exportMatch != null) {
            val indentation = exportMatch.groupValues[1]
            val componentName = exportMatch.groupValues[2]

            val wrapperCode = "${indentation}const ViewWithTracking = withTracking(View)\n\n"

            val insertIndex = exportMatch.range.first
            currentText = currentText.substring(0, insertIndex) + wrapperCode + currentText.substring(insertIndex)
        }

        currentText = currentText.replace(Regex("<View(\\s|/|>)"), "<ViewWithTracking$1")

        currentText = currentText.replace("</View>", "</ViewWithTracking>")

        return currentText
    }
}