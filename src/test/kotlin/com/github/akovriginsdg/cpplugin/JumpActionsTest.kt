package com.github.akovriginsdg.cpplugin.actions.navigation

import com.github.akovriginsdg.cpplugin.settings.NavigationSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.Key
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JLabel

class JumpActionsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        NavigationSettings.instance.state.jumpLinesCount = 5
    }

    fun `test jump down basic`() {
        val text = (0..10).joinToString("\n") { "Line $it" }
        myFixture.configureByText("test.txt", text)

        myFixture.editor.caretModel.moveToOffset(0)

        val action = JumpDownAction()
        performAction(action)

        assertEquals(5, myFixture.editor.caretModel.logicalPosition.line)
    }

    fun `test jump up basic`() {
        val text = (0..10).joinToString("\n") { "Line $it" }
        myFixture.configureByText("test.txt", text)

        val offset = myFixture.editor.document.getLineStartOffset(8)
        myFixture.editor.caretModel.moveToOffset(offset)

        val action = JumpUpAction()
        performAction(action)

        assertEquals(3, myFixture.editor.caretModel.logicalPosition.line)
    }

    fun `test custom settings step`() {
        val text = (0..20).joinToString("\n") { "Line $it" }
        myFixture.configureByText("test.txt", text)
        myFixture.editor.caretModel.moveToOffset(0)

        NavigationSettings.instance.state.jumpLinesCount = 10

        val action = JumpDownAction()
        performAction(action)

        assertEquals(10, myFixture.editor.caretModel.logicalPosition.line)
    }

    fun `test sticky column behavior`() {
        val text = """
            0123456789012345
            Line 1
            Line 2
            Line 3
            Line 4
            Short
            Line 6
            Line 7
            Line 8
            Line 9
            0123456789012345
        """.trimIndent()

        myFixture.configureByText("test.txt", text)

        myFixture.editor.caretModel.moveToLogicalPosition(com.intellij.openapi.editor.LogicalPosition(0, 10))

        val action = JumpDownAction()

        performAction(action)

        val posAfterFirstJump = myFixture.editor.caretModel.logicalPosition
        assertEquals(5, posAfterFirstJump.line)
        assertTrue("Column should be clamped", posAfterFirstJump.column < 10)

        performAction(action)

        val posAfterSecondJump = myFixture.editor.caretModel.logicalPosition
        assertEquals(10, posAfterSecondJump.line)
        assertEquals("Sticky column should restore position to 10", 10, posAfterSecondJump.column)
    }

    fun `test sticky column reset after timeout`() {
        val text = (0..10).joinToString("\n") { "0123456789012345" }
        myFixture.configureByText("test.txt", text)

        myFixture.editor.caretModel.moveToLogicalPosition(com.intellij.openapi.editor.LogicalPosition(0, 10))
        val action = JumpDownAction()

        performAction(action)
        assertEquals(10, myFixture.editor.caretModel.logicalPosition.column)

        val timeKey = Key.findKeyByName("CP_LAST_JUMP_TIME") as Key<Long>
        myFixture.editor.putUserData(timeKey, System.currentTimeMillis() - 2000)

        myFixture.editor.caretModel.moveToLogicalPosition(com.intellij.openapi.editor.LogicalPosition(5, 5))

        performAction(action)

        assertEquals(10, myFixture.editor.caretModel.logicalPosition.line)
        assertEquals("Column should be reset to current (5) after timeout", 5, myFixture.editor.caretModel.logicalPosition.column)
    }

    fun `test selection with shift`() {
        val text = (0..10).joinToString("\n") { "Line $it" }
        myFixture.configureByText("test.txt", text)
        myFixture.editor.caretModel.moveToOffset(0)

        val action = JumpDownAction()

        performAction(action, withShift = true)

        val caret = myFixture.editor.caretModel.primaryCaret
        assertTrue("Selection should exist", caret.hasSelection())

        val startLine = myFixture.editor.document.getLineNumber(caret.selectionStart)
        val endLine = myFixture.editor.document.getLineNumber(caret.selectionEnd)

        assertEquals(0, startLine)
        assertEquals(5, endLine)
    }

    private fun performAction(action: BaseJumpAction, withShift: Boolean = false) {
        val dataContext = DataContext { dataId ->
            if (CommonDataKeys.EDITOR.`is`(dataId)) myFixture.editor
            else if (CommonDataKeys.PROJECT.`is`(dataId)) project
            else null
        }

        val modifiers = if (withShift) InputEvent.SHIFT_MASK else 0

        val dummyInputEvent = KeyEvent(
            JLabel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            modifiers,
            KeyEvent.VK_UNDEFINED,
            KeyEvent.CHAR_UNDEFINED
        )

        val event = TestActionEvent.createTestEvent(
            action,
            dataContext,
            dummyInputEvent
        )

        action.actionPerformed(event)
    }
}