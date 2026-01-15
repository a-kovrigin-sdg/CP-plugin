package com.github.akovriginsdg.cpplugin.actions.mockFileHandler

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

open class CreateMockFileAction : BaseMockFileAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val parent = file?.parent
        val testName = "${file?.nameWithoutExtension}.mock.ts"
        val existingMock = parent?.findChild(testName)


        val isVisible = project != null &&
                file != null &&
                existingMock == null &&
                !file.isDirectory &&
                file.nameSequence.endsWith(".ts") &&
                !file.nameSequence.endsWith(".test.ts") &&
                !file.nameSequence.endsWith(".spec.ts") &&
                !file.nameSequence.endsWith(".mock.ts") &&
                !file.nameSequence.endsWith(".d.ts")

        e.presentation.isEnabledAndVisible = isVisible
        e.presentation.description = "Generates new ${testName}"
        e.presentation.icon = com.intellij.icons.AllIcons.Nodes.Method
        e.presentation.text = "Create Mock File"
    }
}