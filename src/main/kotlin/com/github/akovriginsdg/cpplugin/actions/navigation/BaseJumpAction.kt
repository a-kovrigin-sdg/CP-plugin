package com.github.akovriginsdg.cpplugin.actions.navigation

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.Key
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.math.max
import kotlin.math.min

abstract class BaseJumpAction : AnAction() {

    companion object {
        private val JUMP_COLUMN_KEY = Key.create<Int>("CP_JUMP_COLUMN")
        private val LAST_JUMP_TIME_KEY = Key.create<Long>("CP_LAST_JUMP_TIME")
        private const val RESET_TIMEOUT_MS = 700L
    }

    abstract fun calculateTargetLine(editor: Editor, currentLine: Int): Int

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val caret = editor.caretModel.primaryCaret
        val currentOffset = caret.offset
        val currentLogicalPos = caret.logicalPosition

        val now = System.currentTimeMillis()
        val lastTime = editor.getUserData(LAST_JUMP_TIME_KEY) ?: 0L
        val savedColumn = editor.getUserData(JUMP_COLUMN_KEY)

        val isSequence = savedColumn != null && (now - lastTime) < RESET_TIMEOUT_MS

        val targetColumn = if (isSequence) {
            savedColumn!!
        } else {
            currentLogicalPos.column
        }

        editor.putUserData(JUMP_COLUMN_KEY, targetColumn)
        editor.putUserData(LAST_JUMP_TIME_KEY, now)

        val targetLine = calculateTargetLine(editor, currentLogicalPos.line)

        val targetPos = LogicalPosition(targetLine, targetColumn)
        val newOffset = editor.logicalPositionToOffset(targetPos)

        if (newOffset == currentOffset) return

        val currentSelectionStart = caret.selectionStart
        val currentSelectionEnd = caret.selectionEnd

        val inputEvent = e.inputEvent
        val isShiftPressed = if (inputEvent is KeyEvent) inputEvent.isShiftDown
        else (e.modifiers and InputEvent.SHIFT_MASK) != 0

        val startAnchor = if (isShiftPressed && caret.hasSelection()) {
            when (currentOffset) {
                currentSelectionEnd -> currentSelectionStart
                currentSelectionStart -> currentSelectionEnd
                else -> currentOffset
            }
        } else {
            currentOffset
        }

        caret.moveToOffset(newOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        if (isShiftPressed) {
            val sStart = min(startAnchor, newOffset)
            val sEnd = max(startAnchor, newOffset)
            caret.setSelection(sStart, sEnd)
        } else {
            caret.removeSelection()
        }
    }
}