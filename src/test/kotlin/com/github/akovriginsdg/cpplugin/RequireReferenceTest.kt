package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.requireReferenceResolver.RequireModuleReference

class RequireReferenceTest : BasePluginTest() {

    override fun setUp() {
        super.setUp()
        addFile("${PluginConst.APP_ROOT}/modules/utils/logger.js", "")
    }

    fun `test require reference`() {
        val widgetPath = "${PluginConst.WIDGETS_ROOT}/test/main.js"

        addFile(widgetPath, """
            require('modules/utils/log<caret>ger');
        """.trimIndent())

        myFixture.configureFromTempProjectFile(widgetPath)

        var element = myFixture.file.findElementAt(myFixture.caretOffset)
        var reference = element?.references?.find { it is RequireModuleReference }

        while (element != null && reference == null) {
            element = element.parent
            if (element == null || element is com.intellij.psi.PsiFile) break
            reference = element.references.find { it is RequireModuleReference }
        }

        assertNotNull("RequireModuleReference not found. Folder check failed?", reference)
        assertEquals("logger.js", reference!!.resolve()?.containingFile?.name)
    }
}