package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.actions.CreateMockAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CreateMockActionTest : BasePlatformTestCase() {

    fun `test generates mock for classes and functions but ignores types`() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/users")
        var originalFile: VirtualFile? = null

        WriteAction.runAndWait<Throwable> {
            val file = domainDir.createChildData(this, "user-profile.ts")
            file.setBinaryContent("""
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
             """.trimIndent().toByteArray())
            originalFile = file
        }

        executeAction(originalFile!!)

        val mockFile = domainDir.findChild("user-profile.mock.ts")
        assertNotNull("Mock file should be created", mockFile)

        val content = VfsUtil.loadText(mockFile!!)

        assertTrue(content.contains("class UserProfileMock"))
        assertTrue(content.contains("const validateUserMock = jest.fn"))
        assertFalse(content.contains("UserType"))
        assertFalse(content.contains("UserConfig"))
        assertFalse(content.contains("MAX_USERS"))
        assertTrue(content.contains("import type { UserProfile }"))
    }

    fun `test offering class generation`() {
        val domainDir = myFixture.tempDirFixture.findOrCreateDir("domain/cheers")
        var originalFile: VirtualFile? = null

        WriteAction.runAndWait<Throwable> {
            val file = domainDir.createChildData(this, "cheers-offering.ts")
            file.setBinaryContent("""
                export class CheersOffering extends Offering<any> {
                    constructor(userId: string) { super() }
                    public publicMethod() {}
                }
            """.trimIndent().toByteArray())
            originalFile = file
        }

        executeAction(originalFile!!)

        val mockFile = domainDir.findChild("cheers-offering.mock.ts")
        assertNotNull(mockFile)
        val content = VfsUtil.loadText(mockFile!!)

        assertTrue(content.contains("import { Offering } from '@sdv/commons/offering/index.mock'"))
        assertTrue(content.contains("class CheersOfferingMock extends Offering<"))
        assertFalse("Offering mock should ignore class methods", content.contains("publicMethod = jest.fn"))
    }

    private fun executeAction(file: VirtualFile) {
        val action = object : CreateMockAction() {
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