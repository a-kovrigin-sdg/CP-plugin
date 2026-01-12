package com.github.akovriginsdg.cpplugin.actions.navigation

import com.github.akovriginsdg.cpplugin.settings.NavigationSettings
import com.intellij.openapi.editor.Editor

class JumpUpAction : BaseJumpAction() {
    override fun calculateTargetLine(editor: Editor, currentLine: Int): Int {
        val step = NavigationSettings.instance.state.jumpLinesCount
        return (currentLine - step).coerceAtLeast(0)
    }
}

class JumpDownAction : BaseJumpAction() {
    override fun calculateTargetLine(editor: Editor, currentLine: Int): Int {
        val step = NavigationSettings.instance.state.jumpLinesCount
        val lineCount = editor.document.lineCount
        return (currentLine + step).coerceAtMost(lineCount - 1)
    }
}