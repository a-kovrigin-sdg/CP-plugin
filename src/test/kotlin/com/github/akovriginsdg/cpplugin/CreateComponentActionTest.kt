package com.github.akovriginsdg.cpplugin.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CreateComponentActionTest : BasePlatformTestCase() {

    private lateinit var action: CreateComponentAction

    override fun setUp() {
        super.setUp()
        action = CreateComponentAction()
    }

    fun `test action visibility depends on directory path`() {
        val validDir = myFixture.tempDirFixture.findOrCreateDir("dating-web/orbit/source/components")
        val invalidDir = myFixture.tempDirFixture.findOrCreateDir("other/folder")

        assertTrue("Action should be visible in target folder", isActionVisible(validDir))
        assertFalse("Action should NOT be visible in random folder", isActionVisible(invalidDir))
    }

    fun `test component structure creation`() {
        // 1. Подготовка
        val rootPath = "dating-web/orbit/source/components"
        val componentsDir = myFixture.tempDirFixture.findOrCreateDir(rootPath)

        // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
        // Для Messages.showInputDialog используем setTestInputDialog
        TestDialogManager.setTestInputDialog { message ->
            "UserProfile" // Возвращаем строку, которую "ввел" пользователь
        }

        // На случай, если выскочит ошибка (Messages.showErrorDialog),
        // чтобы тест не завис, а просто нажал OK.
        TestDialogManager.setTestDialog(TestDialog.OK)
        // -------------------------

        // 2. Создаем контекст
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, componentsDir)
            .build()

        val event = TestActionEvent.createTestEvent(action, dataContext)

        // 3. Выполняем
        action.actionPerformed(event)

        // 4. Проверяем
        val createdDir = componentsDir.findChild("user-profile")
        assertNotNull("Directory 'user-profile' was not created", createdDir)

        assertNotNull("index.tsx missing", createdDir?.findChild("index.tsx"))
        assertNotNull("view.tsx missing", createdDir?.findChild("view.tsx"))
        assertNotNull("styles.module.less missing", createdDir?.findChild("styles.module.less"))
    }

    private fun isActionVisible(file: VirtualFile): Boolean {
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()

        val event = TestActionEvent.createTestEvent(action, dataContext)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    override fun tearDown() {
        // Обязательно сбрасываем моки, чтобы не сломать другие тесты
        TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        super.tearDown()
    }
}