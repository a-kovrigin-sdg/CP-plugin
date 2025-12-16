package com.github.akovriginsdg.cpplugin

import com.intellij.psi.PsiElement

class UsageSearcherTest : BasePluginTest() {

    fun `test find usages of js file`() {
        // 1. Создаем файл-цель
        val targetFileVfs = addFile("${PluginConst.APP_ROOT}/modules/dialogs/chat.js", "console.log('chat');")
        val targetPsiFile = myFixture.psiManager.findFile(targetFileVfs)!!

        // 2. Создаем файл-использование
        val usagePath = "${PluginConst.WIDGETS_ROOT}/some-widget/main.js"
        addFile(usagePath, "require('modules/dialogs/chat');")

        // 3. Открываем файл использования, чтобы IDE его точно "увидела" и распарсила
        myFixture.configureFromTempProjectFile(usagePath)

        // 4. Запускаем поиск
        val usages = myFixture.findUsages(targetPsiFile)

        assertEquals("Should find 1 usage", 1, usages.size)

        val foundUsage = usages.first()
        // Проверяем, что использование найдено в main.js
        assertEquals("main.js", foundUsage.element?.containingFile?.name)
    }
}