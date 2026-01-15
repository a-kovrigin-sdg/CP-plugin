package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.actions.offeringTemplateGenerator.CreateOfferingAction
import com.github.akovriginsdg.cpplugin.actions.offeringTemplateGenerator.OfferingInputData
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

class CreateOfferingActionTest : BasePlatformTestCase() {

    private var capturedClassName: String? = null
    private var capturedFileName: String? = null
    private var capturedOfferName: String? = null
    private var createMethodCalled: Boolean = false

    private lateinit var dummyTemplate: FileTemplate

    override fun setUp() {
        super.setUp()
        val manager = FileTemplateManager.getInstance(project)
        dummyTemplate = manager.addTemplate("DummyTemplate", "ts")
        dummyTemplate.text = ""
    }

    override fun tearDown() {
        capturedClassName = null
        capturedFileName = null
        capturedOfferName = null
        createMethodCalled = false
        super.tearDown()
    }

    fun testFileCreationLongName() {
        val action = object : CreateOfferingAction() {
            override fun getUserInput(project: Project): OfferingInputData {
                return OfferingInputData("chatFab", false)
            }

            override fun findTemplate(project: Project): FileTemplate? {
                return dummyTemplate
            }

            override fun createOfferingFile(
                project: Project,
                directory: PsiDirectory,
                template: FileTemplate,
                fileNameWithoutExtension: String,
                className: String,
                offerName: String
            ) {
                createMethodCalled = true
                capturedFileName = fileNameWithoutExtension
                capturedClassName = className
                capturedOfferName = offerName
            }
        }

        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain")
        val event = TestActionEvent.createTestEvent(action, createEventContext(domainDir))

        action.actionPerformed(event)

        Assert.assertTrue("Create file method should be called", createMethodCalled)
        Assert.assertEquals("chat-fab-offering", capturedFileName)
        Assert.assertEquals("ChatFabOffering", capturedClassName)
        Assert.assertEquals("ChatFabOffer", capturedOfferName)
    }

    fun testFileCreationShortName() {
        val action = object : CreateOfferingAction() {
            override fun getUserInput(project: Project): OfferingInputData {
                return OfferingInputData("User", true)
            }

            override fun findTemplate(project: Project): FileTemplate? {
                return dummyTemplate
            }

            override fun createOfferingFile(
                project: Project,
                directory: PsiDirectory,
                template: FileTemplate,
                fileNameWithoutExtension: String,
                className: String,
                offerName: String
            ) {
                createMethodCalled = true
                capturedFileName = fileNameWithoutExtension
                capturedClassName = className
                capturedOfferName = offerName
            }
        }

        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/subfeature")
        val event = TestActionEvent.createTestEvent(action, createEventContext(domainDir))

        action.actionPerformed(event)

        Assert.assertTrue(createMethodCalled)
        Assert.assertEquals("offering", capturedFileName)
        Assert.assertEquals("UserOffering", capturedClassName)
    }

    fun testUpdateVisibility() {
        val action = CreateOfferingAction()

        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain")
        val contextDomain = createEventContext(domainDir)
        val eventDomain = TestActionEvent.createTestEvent(action, contextDomain)

        action.update(eventDomain)
        Assert.assertTrue(eventDomain.presentation.isEnabledAndVisible)

        val servicesDir = myFixture.tempDirFixture.findOrCreateDir("services")
        val contextServices = createEventContext(servicesDir)
        val eventServices = TestActionEvent.createTestEvent(action, contextServices)

        action.update(eventServices)
        Assert.assertFalse(eventServices.presentation.isEnabledAndVisible)
    }

    private fun createEventContext(file: VirtualFile): DataContext {
        return SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()
    }
}