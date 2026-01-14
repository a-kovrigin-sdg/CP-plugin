package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.actions.CreateDomainInjectionAction
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import java.util.*

class CreateDomainInjectionTest : BasePlatformTestCase() {

    private lateinit var domainDir: VirtualFile
    private lateinit var projectRoot: VirtualFile
    private lateinit var dummyTemplate: FileTemplate

    private var capturedProps: Properties? = null
    private var methodCalled = false

    override fun setUp() {
        super.setUp()

        projectRoot = myFixture.tempDirFixture.findOrCreateDir(".")

        WriteAction.runAndWait<Throwable> {
            VfsUtil.createDirectoryIfMissing(projectRoot, PluginConst.WEB_DOMAIN_PATH)
            VfsUtil.createDirectoryIfMissing(projectRoot, PluginConst.MOBILE_DOMAIN_PATH)

            domainDir = VfsUtil.createDirectoryIfMissing(projectRoot, "${PluginConst.DOMAIN_ROOT_DIR}/users")
        }

        val manager = FileTemplateManager.getInstance(project)
        dummyTemplate = manager.addTemplate("Dummy", "ts")
        dummyTemplate.text = ""
    }

    override fun tearDown() {
        capturedProps = null
        methodCalled = false
        super.tearDown()
    }

    fun `test inject class generation logic`() {
        val className = "UserProfile"
        val expectedKebab = "user-profile"

        val action = object : CreateDomainInjectionAction() {
            override fun getUserInput(project: Project) = className
            override fun getTemplate(project: Project, name: String) = dummyTemplate

            override fun runWriteGeneration(
                project: Project,
                projectRoot: VirtualFile,
                currentPsiDir: PsiDirectory,
                kebabName: String,
                subPath: String,
                props: Properties,
                tplContracts: FileTemplate,
                tplAggregator: FileTemplate,
                tplImpl: FileTemplate
            ) {
                methodCalled = true
                capturedProps = props

                Assert.assertEquals("user-profile", kebabName)
                Assert.assertEquals("users", subPath)
            }
        }

        action.generateDomainFiles(project, domainDir, className, projectRoot)

        Assert.assertTrue("Generation method was not called", methodCalled)
        Assert.assertNotNull(capturedProps)

        val props = capturedProps!!
        Assert.assertEquals("UserProfile", props.getProperty("TYPE_NAME"))
        Assert.assertEquals("user-profile", props.getProperty("KEBAB_NAME"))
    }

    fun `test inject function generation logic`() {
        val funcName = "validateEmail"

        val action = object : CreateDomainInjectionAction() {
            override fun getUserInput(project: Project) = funcName
            override fun getTemplate(project: Project, name: String) = dummyTemplate

            override fun runWriteGeneration(
                project: Project,
                projectRoot: VirtualFile,
                currentPsiDir: PsiDirectory,
                kebabName: String,
                subPath: String,
                props: Properties,
                tplContracts: FileTemplate,
                tplAggregator: FileTemplate,
                tplImpl: FileTemplate
            ) {
                methodCalled = true
                capturedProps = props
            }
        }

        action.generateDomainFiles(project, domainDir, funcName, projectRoot)

        Assert.assertTrue(methodCalled)
        val props = capturedProps!!

        Assert.assertEquals("ValidateEmail", props.getProperty("TYPE_NAME"))
        Assert.assertEquals("validateEmail", props.getProperty("FUNC_NAME"))
        Assert.assertEquals("validate-email", props.getProperty("KEBAB_NAME"))
    }
}