package com.github.akovriginsdg.cpplugin.actions.mockFileHandler

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

open class UpdateMockFileAction : BaseMockFileAction() {
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        val isVisible = project != null &&
                file != null &&
                !file.isDirectory &&
                file.nameSequence.endsWith(".mock.ts")

        e.presentation.isEnabledAndVisible = isVisible
        e.presentation.text = "Update Mock File"
        e.presentation.icon = com.intellij.icons.AllIcons.Actions.Refresh
    }
}