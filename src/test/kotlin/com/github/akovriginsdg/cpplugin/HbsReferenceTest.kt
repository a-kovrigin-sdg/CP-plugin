package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.hbsReferenceResolver.HbsReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HbsReferenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Создаем структуру файлов в памяти перед каждым тестом

        // 1. Для теста testResolveToMainWhenProfileMissing
        myFixture.addFileToProject(
            "dating-web/public/app/widgets/test-widget/profile/main.js",
            "// main"
        )

        // 2. Для теста testResolveToNamedWidget
        myFixture.addFileToProject(
            "dating-web/public/app/widgets/test-widget/profile/test-named-widget/named.js",
            "// named"
        )

        // 3. Папка для testResolveToFolderWhenFilesMissing создастся автоматически,
        // так как мы создали файлы внутри (test-named-widget), но для чистоты эксперимента
        // мы будем искать в пустой папке, которую создадим "неявно" отсутствием файлов.

        // 4. Для теста testResolveL10n
        myFixture.addFileToProject(
            "dating-web/l10n/buttons/en-US.json",
            "{}"
        )
    }

    // Хелпер для создания "абсолютного" пути, как это делает Contributor
    private fun getAbsolutePath(subPath: String): String {
        val basePath = myFixture.project.basePath ?: ""
        return "$basePath/$subPath"
    }

    // Создаем фиктивный элемент (якорь)
    private fun createMockElement() = myFixture.configureByText("test.hbs", "<link />").findElementAt(0)!!

    fun testResolveToMainWhenProfileMissing() {
        // Путь относительно корня проекта
        val relativeFolder = "dating-web/public/app/widgets/test-widget/profile"

        val missingProfilePath = getAbsolutePath("$relativeFolder/profile.js") // Нет файла
        val existingMainPath = getAbsolutePath("$relativeFolder/main.js")     // Есть файл

        val reference = HbsReference(
            element = createMockElement(),
            textRange = TextRange(0, 0),
            fileCandidates = listOf(missingProfilePath, existingMainPath),
            fallbackPath = getAbsolutePath(relativeFolder)
        )

        val resolved = reference.resolve()

        assertNotNull("Should resolve to main.js", resolved)
        assertEquals("main.js", resolved!!.containingFile.name)
    }

    fun testResolveToNamedWidget() {
        val relativeFolder = "dating-web/public/app/widgets/test-widget/profile/test-named-widget"

        val missingProfilePath = getAbsolutePath("$relativeFolder/test-named-widget.js")
        val existingMainPath = getAbsolutePath("$relativeFolder/named.js")

        val reference = HbsReference(
            element = createMockElement(),
            textRange = TextRange(0, 0),
            fileCandidates = listOf(missingProfilePath, existingMainPath),
            fallbackPath = getAbsolutePath(relativeFolder)
        )

        val resolved = reference.resolve()

        assertNotNull("Should resolve to named.js", resolved)
        assertEquals("named.js", resolved!!.containingFile.name)
    }

    fun testResolveToFolderWhenFilesMissing() {
        // Используем папку, где мы НЕ создавали main.js или profile.js
        val relativeFolder = "dating-web/public/app/widgets/empty-folder"

        // Создадим директорию явно (пустую), чтобы она существовала в VFS
        myFixture.tempDirFixture.findOrCreateDir(relativeFolder)

        val missingProfile = getAbsolutePath("$relativeFolder/profile.js")
        val missingMain = getAbsolutePath("$relativeFolder/main.js")
        val folderPath = getAbsolutePath(relativeFolder)

        val reference = HbsReference(
            element = createMockElement(),
            textRange = TextRange(0, 0),
            fileCandidates = listOf(missingProfile, missingMain),
            fallbackPath = folderPath
        )

        val resolved = reference.resolve()

        assertNotNull("Should resolve to directory", resolved)
        assertTrue("Resolved element should be a directory", resolved is PsiDirectory)
        assertEquals("empty-folder", (resolved as PsiDirectory).name)
    }

    fun testResolveL10n() {
        val l10nPath = getAbsolutePath("dating-web/l10n/buttons/en-US.json")

        val reference = HbsReference(
            element = createMockElement(),
            textRange = TextRange(0, 0),
            fileCandidates = listOf(l10nPath),
            fallbackPath = null
        )

        val resolved = reference.resolve()

        assertNotNull("Should resolve localization file", resolved)
        assertEquals("en-US.json", resolved!!.containingFile.name)
    }
}