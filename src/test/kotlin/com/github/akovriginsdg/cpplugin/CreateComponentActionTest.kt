package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import java.util.Properties

class CreateComponentActionTest : BasePlatformTestCase() {

    private lateinit var componentsDir: PsiDirectory
    private lateinit var dummyTemplate: FileTemplate

    private var methodCalled = false
    private var capturedFolderName: String? = null
    private var capturedComponentName: String? = null

    override fun setUp() {
        super.setUp()

        val root = myFixture.tempDirFixture.findOrCreateDir(".")
        WriteAction.runAndWait<Throwable> {
            val vfsDir = VfsUtil.createDirectoryIfMissing(root, PluginConst.WEB_COMPONENTS_FOLDER)
            componentsDir = psiManager.findDirectory(vfsDir)!!
        }

        val manager = FileTemplateManager.getInstance(project)
        dummyTemplate = manager.addTemplate("Dummy", "tsx")
        dummyTemplate.text = ""
    }

    override fun tearDown() {
        methodCalled = false
        capturedFolderName = null
        capturedComponentName = null
        super.tearDown()
    }

    fun `test component structure generation logic`() {
        val inputName = "UserProfile"
        val expectedKebab = "user-profile"

        val action = object : CreateComponentAction() {
            override fun getUserInput(project: Project) = inputName

            override fun getTemplate(project: Project, name: String) = dummyTemplate

            override fun runWriteCreation(
                project: Project,
                parentDir: PsiDirectory,
                folderName: String,
                componentName: String,
                tplIndex: FileTemplate,
                tplView: FileTemplate,
                tplStyle: FileTemplate
            ) {
                methodCalled = true
                capturedFolderName = folderName
                capturedComponentName = componentName

                Assert.assertEquals(dummyTemplate, tplIndex)
            }
        }

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, componentsDir.virtualFile)
            .build()
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)

        Assert.assertTrue("Write creation method was not called", methodCalled)
        Assert.assertEquals("Folder name mismatch", expectedKebab, capturedFolderName)
        Assert.assertEquals("Component name mismatch", inputName, capturedComponentName)
    }

    fun `test action visibility`() {
        val action = CreateComponentAction()

        val dataContextValid = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, componentsDir.virtualFile)
            .build()
        val eventValid = TestActionEvent.createTestEvent(action, dataContextValid)

        action.update(eventValid)
        Assert.assertTrue("Action should be visible in components folder", eventValid.presentation.isEnabledAndVisible)

        val root = myFixture.tempDirFixture.findOrCreateDir(".")
        val dataContextInvalid = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, root)
            .build()
        val eventInvalid = TestActionEvent.createTestEvent(action, dataContextInvalid)

        action.update(eventInvalid)
        Assert.assertFalse("Action should NOT be visible in root", eventInvalid.presentation.isEnabledAndVisible)
    }
}