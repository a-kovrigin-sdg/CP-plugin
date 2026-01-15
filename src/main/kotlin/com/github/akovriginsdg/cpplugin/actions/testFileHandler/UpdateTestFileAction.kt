package com.github.akovriginsdg.cpplugin.actions.testFileHandler

import com.github.akovriginsdg.cpplugin.actions.BaseTestFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

open class UpdateTestFileAction : BaseTestFileAction() {
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        val isVisible = project != null &&
                file != null &&
                !file.isDirectory &&
                file.nameSequence.endsWith(".test.ts")

        e.presentation.isEnabledAndVisible = isVisible
        e.presentation.text = "Update Mocks Imports"
        e.presentation.icon = com.intellij.icons.AllIcons.Actions.Refresh
    }
}