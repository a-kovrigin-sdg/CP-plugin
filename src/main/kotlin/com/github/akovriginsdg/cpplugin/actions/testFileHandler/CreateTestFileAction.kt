package com.github.akovriginsdg.cpplugin.actions.testFileHandler

import com.github.akovriginsdg.cpplugin.actions.BaseTestFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

open class CreateTestFileAction : BaseTestFileAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val parent = file?.parent
        val testName = "${file?.nameWithoutExtension}.test.ts"
        val existingTest = parent?.findChild(testName)

        val isVisible = project != null &&
                file != null &&
                !file.isDirectory &&
                existingTest == null &&
                file.nameSequence.endsWith(".ts") &&
                !file.nameSequence.endsWith(".test.ts") &&
                !file.nameSequence.endsWith(".spec.ts") &&
                !file.nameSequence.endsWith(".mock.ts") &&
                !file.nameSequence.endsWith(".d.ts")

        e.presentation.isEnabledAndVisible = isVisible
        e.presentation.text = "Create Test File"
        e.presentation.icon = com.intellij.icons.AllIcons.Modules.GeneratedTestRoot
    }
}