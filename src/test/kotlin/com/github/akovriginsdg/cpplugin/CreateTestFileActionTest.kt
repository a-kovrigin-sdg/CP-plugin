package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.actions.testFileHandler.CreateTestFileAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CreateTestFileActionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        buildProjectStructure()
    }

    fun testCreateNewTestFile() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/users")

        val content = """
            import { Observable } from 'rxjs' // External lib (ignore)
            import { Config } from '@sdv/domain/config' // Folder -> index.mock
            import { UserRole } from '@sdv/domain/users/role' // File -> .mock
            
            export class UserProfile {
                constructor() {}
            }
        """.trimIndent()

        val sourceFile = createTestFile(domainDir, "profile.ts", content)

        executeAction(sourceFile)
        saveAllDocuments()

        val testFile = domainDir.findChild("profile.test.ts")
        assertNotNull("Test file must be created", testFile)

        val testContent = VfsUtil.loadText(testFile!!)

        assertTrue("Should mock folder index", testContent.contains("jest.useExtendedMock('@sdv/domain/config/index.mock')"))
        assertTrue("Should mock specific file", testContent.contains("jest.useExtendedMock('@sdv/domain/users/role.mock')"))

        assertFalse("Should not mock libs", testContent.contains("rxjs"))

        assertTrue(testContent.contains("import { Config } from '@sdv/domain/config/index.mock'"))
        assertTrue(testContent.contains("import { UserRole } from '@sdv/domain/users/role.mock'"))
    }

    fun testIgnoreTypeImports() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/types_check")

        val content = """
            import type { SomeDto } from '@sdv/domain/dto'
            import { RealClass } from '@sdv/domain/real'
            
            export class Checker {}
        """.trimIndent()

        val sourceFile = createTestFile(domainDir, "checker.ts", content)

        executeAction(sourceFile)
        saveAllDocuments()

        val testContent = VfsUtil.loadText(domainDir.findChild("checker.test.ts")!!)

        assertFalse("Should ignore type imports", testContent.contains("jest.useExtendedMock('@sdv/domain/dto"))

        assertTrue("Should process value imports", testContent.contains("jest.useExtendedMock('@sdv/domain/real.mock')"))
    }

    fun testUpdateExistingTestFile() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/update_flow")

        val sourceContent = """
            import { OldDep } from '@sdv/domain/old'
            import { NewDep } from '@sdv/domain/new'
            export class MyService {}
        """.trimIndent()
        val sourceFile = createTestFile(domainDir, "service.ts", sourceContent)

        val testContent = """
            jest.useExtendedMock('@sdv/domain/old.mock')
            import { OldDep } from '@sdv/domain/old.mock'
            
            describe('MyService', () => {})
        """.trimIndent()
        createTestFile(domainDir, "service.test.ts", testContent)

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        executeAction(sourceFile)
        saveAllDocuments()

        val finalContent = VfsUtil.loadText(domainDir.findChild("service.test.ts")!!)

        assertTrue(finalContent.contains("jest.useExtendedMock('@sdv/domain/old.mock')"))

        assertTrue("New mock call must be added", finalContent.contains("jest.useExtendedMock('@sdv/domain/new.mock')"))

        assertFalse("New imports should NOT be added on update", finalContent.contains("import { NewDep }"))
    }

    fun testActionFromTestFileContext() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/context_check")

        val sourceContent = """
            import { Dependency } from '@sdv/domain/dep'
            export class Logic {}
        """.trimIndent()
        createTestFile(domainDir, "logic.ts", sourceContent)

        val testFile = createTestFile(domainDir, "logic.test.ts", "// empty")

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        executeAction(testFile)
        saveAllDocuments()

        val finalContent = VfsUtil.loadText(testFile)

        assertTrue("Should update test when action invoked on test file",
            finalContent.contains("jest.useExtendedMock('@sdv/domain/dep.mock')"))
    }

    private fun buildProjectStructure() {
        val root = myFixture.tempDirFixture.findOrCreateDir("domain")

        WriteAction.runAndWait<Throwable> {
            root.createChildDirectory(this, "config").createChildData(this, "index.mock.ts")
            root.createChildDirectory(this, "users").createChildData(this, "role.mock.ts")
            root.createChildData(this, "dto.mock.ts")
            root.createChildData(this, "real.mock.ts")
            root.createChildData(this, "old.mock.ts")
            root.createChildData(this, "new.mock.ts")
            root.createChildData(this, "dep.mock.ts")
        }
    }

    private fun createTestFile(dir: VirtualFile, name: String, content: String): VirtualFile {
        var file: VirtualFile? = null
        WriteAction.runAndWait<Throwable> {
            val child = dir.createChildData(this, name)
            child.setBinaryContent(content.toByteArray())
            file = child
        }
        return file!!
    }

    private fun saveAllDocuments() {
        WriteAction.runAndWait<Throwable> {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }

    private fun executeAction(file: VirtualFile) {
        val action = object : CreateTestFileAction() {}

        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()

        action.actionPerformed(TestActionEvent.createTestEvent(action, context))
    }
}
