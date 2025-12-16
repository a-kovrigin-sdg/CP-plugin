package com.github.akovriginsdg.cpplugin

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class BaseContributorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()

        // 1. ХАК: Заставляем IDE воспринимать .hbs файлы как XML в тестах.
        // Это нужно, чтобы XmlPatterns (поиск тегов/атрибутов) работали без загрузки тяжелого плагина Handlebars.
        runWriteAction {
            val fileTypeManager = FileTypeManager.getInstance()
            fileTypeManager.associatePattern(XmlFileType.INSTANCE, "*.hbs")

            // Гарантируем, что JS не PlainText (обычно не требуется, но для надежности)
            fileTypeManager.removeAssociatedExtension(PlainTextFileType.INSTANCE, "js")
        }

        // 2. ГАРАНТИЯ: Создаем папку dating-web прямо в корне проекта.
        // Это нужно, чтобы PluginUtils.isTargetProject(project) возвращал true.
        runWriteAction {
            val projectDir = myFixture.project.baseDir
            if (projectDir.findChild("dating-web") == null) {
                projectDir.createChildDirectory(this, "dating-web")
            }
        }
    }

    // Помощник для создания файлов с правильными путями
    protected fun createAndConfigure(path: String, content: String) {
        // addFileToProject создает файл внутри source root (обычно /src)
        // Но PluginUtils ищет relative path.
        myFixture.addFileToProject(path, content)
        myFixture.configureFromTempProjectFile(path)
    }
}