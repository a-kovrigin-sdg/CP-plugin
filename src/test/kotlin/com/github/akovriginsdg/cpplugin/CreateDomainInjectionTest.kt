package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.BasePluginTest
import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager

class CreateDomainInjectionTest : BasePluginTest() {

    private lateinit var domainDir: VirtualFile
    private val action = CreateDomainInjectionAction()

    override fun setUp() {
        super.setUp()

        WriteAction.runAndWait<Throwable> {
            val root = myFixture.tempDirFixture.getFile(".")!!

            VfsUtil.createDirectoryIfMissing(root, PluginConst.WEB_DOMAIN_PATH)
            VfsUtil.createDirectoryIfMissing(root, PluginConst.MOBILE_DOMAIN_PATH)

            domainDir = VfsUtil.createDirectoryIfMissing(root, "${PluginConst.DOMAIN_ROOT_DIR}/users")
        }
    }

    fun `test inject class generation`() {
        val className = "UserProfile"
        val kebabName = "user-profile"

        val projectRoot = myFixture.tempDirFixture.getFile(".")!!

        action.generateDomainFiles(project, domainDir, className, projectRoot)

        val contractsFile = domainDir.findChild("$kebabName.contracts.ts")
        val mainFile = domainDir.findChild("$kebabName.ts")

        assertNotNull("Contracts file not created", contractsFile)
        assertNotNull("Main file not created", mainFile)

        val expectedContract = String.format(PluginConst.TPL_DOMAIN_CONTRACTS_CLASS, className)
        assertEquals(expectedContract, getFileContent(contractsFile!!))

        val expectedAggregator = String.format(
            PluginConst.TPL_DOMAIN_AGGREGATOR,
            PluginConst.DOMAIN_IMPORT_PREFIX,
            "users/$kebabName",
            kebabName
        )
        assertEquals(expectedAggregator, getFileContent(mainFile!!))

        checkPlatformFile(
            "${PluginConst.WEB_DOMAIN_PATH}/users",
            kebabName,
            className,
            "users/$kebabName.contracts",
            isFunction = false
        )

        checkPlatformFile(
            "${PluginConst.MOBILE_DOMAIN_PATH}/users",
            kebabName,
            className,
            "users/$kebabName.contracts",
            isFunction = false
        )
    }

    fun `test inject function generation`() {
        val inputName = "validateEmail"
        val pascalName = "ValidateEmail"
        val camelName = "validateEmail"
        val kebabName = "validate-email"

        val projectRoot = myFixture.tempDirFixture.getFile(".")!!

        action.generateDomainFiles(project, domainDir, inputName, projectRoot)

        val contractsFile = domainDir.findChild("$kebabName.contracts.ts")
        val mainFile = domainDir.findChild("$kebabName.ts")

        assertNotNull(contractsFile)
        assertNotNull(mainFile)

        val expectedContract = String.format(PluginConst.TPL_DOMAIN_CONTRACTS_FUNCTION, pascalName)
        assertEquals(expectedContract, getFileContent(contractsFile!!))

        val expectedAggregator = String.format(
            PluginConst.TPL_DOMAIN_AGGREGATOR,
            PluginConst.DOMAIN_IMPORT_PREFIX,
            "users/$kebabName",
            kebabName
        )
        assertEquals(expectedAggregator, getFileContent(mainFile!!))

        checkPlatformFile(
            "${PluginConst.WEB_DOMAIN_PATH}/users",
            kebabName,
            pascalName,
            "users/$kebabName.contracts",
            isFunction = true,
            funcName = camelName
        )
    }

    private fun checkPlatformFile(
        path: String,
        fileName: String,
        typeName: String,
        importPath: String,
        isFunction: Boolean,
        funcName: String = ""
    ) {
        val root = myFixture.tempDirFixture.getFile(".")!!

        val dir = root.findFileByRelativePath(path)
        assertNotNull("Target dir not found: $path", dir)

        val file = dir!!.findChild("$fileName.ts")
        assertNotNull("Implementation file missing in $path", file)

        val content = getFileContent(file!!)

        val expected = if (isFunction) {
            String.format(
                PluginConst.TPL_DOMAIN_INJECTION_TARGET_FUNCTION,
                typeName,
                PluginConst.DOMAIN_IMPORT_PREFIX,
                importPath,
                funcName, typeName, funcName, funcName
            )
        } else {
            String.format(
                PluginConst.TPL_DOMAIN_INJECTION_TARGET_CLASS,
                typeName,
                PluginConst.DOMAIN_IMPORT_PREFIX,
                importPath,
                typeName, typeName, typeName, typeName, typeName
            )
        }

        assertEquals("Content mismatch in $path", expected, content)
    }

    private fun getFileContent(file: VirtualFile): String {
        file.refresh(false, false)

        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile != null) {
            return psiFile.text
        }

        return String(file.contentsToByteArray())
    }
}