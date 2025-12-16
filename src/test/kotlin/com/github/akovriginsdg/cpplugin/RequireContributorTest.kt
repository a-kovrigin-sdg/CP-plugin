package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.requireReferenceResolver.RequireModuleReference

class RequireContributorTest : BaseContributorTest() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("dating-web/public/app/modules/utils/logger.js", "")
    }

    fun testRequireInsideWidget() {
        val widgetPath = "dating-web/public/app/widgets/my-widget/main.js"

        createAndConfigure(widgetPath, """
            define(function(require) {
                const logger = require('modules/utils/log<caret>ger');
            });
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)?.parent
        val reference = element?.references?.find { it is RequireModuleReference }

        assertNotNull("Require reference not found inside widgets", reference)
        assertEquals("logger.js", reference!!.resolve()?.containingFile?.name)
    }
}