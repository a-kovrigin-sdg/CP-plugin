package com.github.akovriginsdg.cpplugin

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

abstract class BasePluginTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()

        // ГЛАВНОЕ ИСПРАВЛЕНИЕ:
        // Создаем папку-маркер "dating-web" прямо в корне проекта (baseDir).
        // Это гарантирует, что PluginUtils.isTargetProject(project) вернет true.
        runWriteAction {
            val projectDir = getProjectDirOrFile()
            if (projectDir.findChild(PluginConst.DATING_WEB_ROOT) == null) {
                projectDir.createChildDirectory(this, PluginConst.DATING_WEB_ROOT)
            }
        }
    }

    // Вспомогательный метод для получения корня проекта в тестах
    private fun getProjectDirOrFile(): VirtualFile {
        return myFixture.project.baseDir
            ?: throw IllegalStateException("Project base dir is null")
    }

    // Хелпер, чтобы не писать длинный путь каждый раз
    protected fun addFile(path: String, content: String): VirtualFile {
        return myFixture.addFileToProject(path, content).virtualFile
    }
}