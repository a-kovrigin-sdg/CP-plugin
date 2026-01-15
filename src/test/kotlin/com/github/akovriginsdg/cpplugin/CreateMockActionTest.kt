package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.actions.mockFileHandler.CreateMockFileAction
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

class CreateMockActionTest : BasePlatformTestCase() {

    fun testGeneratesMockForClassesAndFunctions() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/users")

        val content = """
             import { Observable } from 'rxjs'
             export class UserProfile {
                 readonly name: string
                 constructor(id: string) {}
                 update(): void {}
             }
             export function validateUser(id: string) { return true }
             export const formatName = (name: string) => name.toUpperCase()
             export type UserType = 'admin' | 'guest'
             export interface UserConfig { id: string }
             export const MAX_USERS = 100
        """.trimIndent()

        val originalFile = createTestFile(domainDir, "user-profile.ts", content)

        executeAction(originalFile)

        val mockFile = domainDir.findChild("user-profile.mock.ts")
        assertNotNull(mockFile)
        val mockContent = VfsUtil.loadText(mockFile!!)

        assertTrue(mockContent.contains("class UserProfileMock"))
        assertTrue(mockContent.contains("const validateUserMock = jest.fn"))
        assertFalse(mockContent.contains("UserType"))
        assertTrue(mockContent.contains("import type { UserProfile }"))
    }

    fun testOfferingClassGeneration() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/cheers")

        val content = """
            export class CheersOffering extends Offering<any> {
                constructor(userId: string) { super() }
                public publicMethod() {}
            }
        """.trimIndent()

        val originalFile = createTestFile(domainDir, "cheers-offering.ts", content)

        executeAction(originalFile)

        val mockFile = domainDir.findChild("cheers-offering.mock.ts")
        val mockContent = VfsUtil.loadText(mockFile!!)
        assertTrue(mockContent.contains("import { Offering } from '@sdv/commons/offering/index.mock'"))
        assertTrue(mockContent.contains("class CheersOfferingMock extends Offering<"))
    }

    fun testHandlesComplexConstructorSignatures() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/complex")

        val content = """
            export class Complex {
                constructor(private readonly id: string, public service: Service) {}
            }
        """.trimIndent()

        val originalFile = createTestFile(domainDir, "complex.ts", content)

        executeAction(originalFile)

        val mockContent = VfsUtil.loadText(domainDir.findChild("complex.mock.ts")!!)
        assertTrue(mockContent.contains("private constructor(id: string, service: Service)"))
        assertTrue(mockContent.contains("new ComplexMock(id, service)"))
    }

    fun testSmartUpdate() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/update_test")

        val sourceContent = """
            export class MyService {
                oldMethod() {}
                newMethod() {} // NEW
            }
        """.trimIndent()
        val originalFile = createTestFile(domainDir, "service.ts", sourceContent)

        val mockContent = """
            import { MyService } from './service'
            class MyServiceMock implements Public<MyService> {
                static readonly shared = jest.fn()
                private constructor() {}
                readonly oldMethod = jest.fn() // OLD
            }
            export { MyServiceMock as MyService }
        """.trimIndent()
        createTestFile(domainDir, "service.mock.ts", mockContent)

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        executeAction(originalFile)

        saveAllDocuments()

        val mockFile = domainDir.findChild("service.mock.ts")
        val finalContent = VfsUtil.loadText(mockFile!!)

        assertTrue("Old method must be preserved", finalContent.contains("readonly oldMethod = jest.fn()"))
        assertTrue("New method must be added", finalContent.contains("readonly newMethod = jest.fn<"))
        assertTrue("Inserted inside class", finalContent.indexOf("newMethod") > finalContent.indexOf("MyServiceMock"))
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
        val action = object : CreateMockFileAction() {
            override fun createMockFile(
                project: Project,
                directory: VirtualFile,
                fileNameWithoutExt: String,
                importsCode: String,
                bodyCode: String
            ) {
                WriteAction.runAndWait<Throwable> {
                    val targetName = "$fileNameWithoutExt.ts"
                    directory.findChild(targetName)?.delete(this)
                    val newFile = directory.createChildData(this, targetName)
                    val fullContent = "$importsCode\n\n$bodyCode"
                    newFile.setBinaryContent(fullContent.toByteArray())
                }
            }
        }

        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()

        action.actionPerformed(TestActionEvent.createTestEvent(action, context))
    }
}