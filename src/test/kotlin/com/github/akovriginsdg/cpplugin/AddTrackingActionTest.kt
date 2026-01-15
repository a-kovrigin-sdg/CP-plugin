package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.github.akovriginsdg.cpplugin.PluginUtils
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import java.io.File

class AddTrackingActionTest : BasePlatformTestCase() {

    private lateinit var dummyTemplate: FileTemplate

    override fun setUp() {
        super.setUp()
        val manager = FileTemplateManager.getInstance(project)
        dummyTemplate = manager.addTemplate("Dummy", "tsx")
        dummyTemplate.text = ""
    }

    fun `test text modification logic`() {
        val action = AddTrackingAction()

        val original = """
            import { memo } from 'react'
            import View from './view'
            import { something } from 'lib'

            export const UserProfile = memo((props: Props) => {
                return (
                    <View id="1" />
                )
            })
        """.trimIndent()

        val modified = action.modifyIndexContent(original)

        Assert.assertTrue(modified.contains("import withTracking from './tracking'"))
        Assert.assertTrue(modified.indexOf("'lib'") < modified.indexOf("'./tracking'"))

        Assert.assertTrue(modified.contains("const ViewWithTracking = withTracking(View)"))
        Assert.assertTrue(modified.indexOf("const ViewWithTracking") < modified.indexOf("export const UserProfile"))

        Assert.assertTrue(modified.contains("<ViewWithTracking id=\"1\" />"))
        Assert.assertFalse(modified.contains("<View id=\"1\" />"))
    }

    fun `test text modification with existing ViewWithTracking doesn't duplicate`() {
        val action = AddTrackingAction()
        val original = """
            import withTracking from './tracking'
            export const A = () => <ViewWithTracking />
        """.trimIndent()

        val modified = action.modifyIndexContent(original)
    }

    fun `test complex jsx replacement`() {
        val action = AddTrackingAction()
        val original = """
            import View from './view'
            export const A = () => (
                <View>
                    <View prop={true}></View>
                </View>
            )
        """.trimIndent()

        val modified = action.modifyIndexContent(original)

        Assert.assertTrue(modified.contains("<ViewWithTracking>"))
        Assert.assertTrue(modified.contains("<ViewWithTracking prop={true}></ViewWithTracking>"))
        Assert.assertTrue(modified.contains("</ViewWithTracking>"))
    }

    fun `test action finds files and triggers creation`() {
        val componentsDir = myFixture.tempDirFixture.findOrCreateDir("dating-web/orbit/source/components/user-profile")
        val indexFile = myFixture.addFileToProject("dating-web/orbit/source/components/user-profile/index.tsx", "export const A = () => {}")

        var writeCalled = false
        val action = object : AddTrackingAction() {
            override fun getTemplate(project: Project, name: String) = dummyTemplate

            override fun runWriteModification(
                project: Project,
                dir: PsiDirectory,
                indexFile: com.intellij.openapi.vfs.VirtualFile,
                template: FileTemplate
            ) {
                writeCalled = true
                Assert.assertEquals("user-profile", dir.name)
                Assert.assertEquals("index.tsx", indexFile.name)
            }
        }

        val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
            .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
            .add(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE, indexFile.virtualFile)
            .build()

        val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)

        Assert.assertTrue("Write modification should be triggered", writeCalled)
    }
}