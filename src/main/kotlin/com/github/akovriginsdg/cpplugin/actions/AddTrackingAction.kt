package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.github.akovriginsdg.cpplugin.PluginUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.openapi.application.runWriteAction

class AddTrackingAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

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
        // Кнопка активна, только если tracking.tsx еще нет (или он невалиден/удален)
        val noTracking = trackingFile == null || !trackingFile.isValid

        e.presentation.isEnabledAndVisible = hasIndex && noTracking
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!selectedFile.isValid) return

        val dir = if (selectedFile.isDirectory) selectedFile else selectedFile.parent
        if (dir == null || !dir.isValid) return

        val indexFile = dir.findChild("index.tsx")
        if (indexFile == null || !indexFile.isValid) return

        executeCommand(project, "Add Tracking HOC") {
            runWriteAction {
                try {
                    // 1. Создаем файл tracking.tsx
                    createTrackingFile(dir)

                    // 2. Модифицируем index.tsx
                    modifyIndexFile(project, indexFile)
                } catch (ex: Exception) {
                    Messages.showErrorDialog(project, "Error adding tracking: ${ex.message}", "Error")
                }
            }
        }
    }

    private fun createTrackingFile(dir: VirtualFile) {
        val fileName = "tracking.tsx"

        val newFile = dir.createChildData(this, fileName)

        newFile.setBinaryContent(PluginConst.TPL_TRACKING.toByteArray())

        newFile.refresh(false, false)
    }

    private fun modifyIndexFile(project: Project, indexFile: VirtualFile) {
        if (!indexFile.isValid) return

        val document = FileDocumentManager.getInstance().getDocument(indexFile) ?: return
        val text = document.text

        if (text.contains("import withTracking")) return

        val newText = StringBuilder(text)

        // ШАГ A: Добавляем импорт
        val lastImportIndex = text.lastIndexOf("import ")
        if (lastImportIndex != -1) {
            val endOfLine = text.indexOf("\n", lastImportIndex)
            if (endOfLine != -1) {
                newText.insert(endOfLine + 1, "import withTracking from './tracking'\n")
            }
        } else {
            newText.insert(0, "import withTracking from './tracking'\n")
        }

        var currentString = newText.toString()

        // ШАГ B: Создаем обертку
        val exportIndex = currentString.indexOf("export const")
        if (exportIndex != -1) {
            val insertPos = exportIndex
            val wrapperCode = "const ViewWithTracking = withTracking(View)\n\n"
            currentString = currentString.substring(0, insertPos) + wrapperCode + currentString.substring(insertPos)
        }

        // ШАГ C: Заменяем использование
        currentString = currentString.replace(Regex("<View(\\s|>)"), "<ViewWithTracking$1")
        currentString = currentString.replace("</View>", "</ViewWithTracking>")

        // Применяем изменения
        document.setText(currentString)
        PsiDocumentManager.getInstance(project).commitDocument(document)

        // Открываем файл
        if (indexFile.isValid) {
            FileEditorManager.getInstance(project).openFile(indexFile, true)
        }
    }
}