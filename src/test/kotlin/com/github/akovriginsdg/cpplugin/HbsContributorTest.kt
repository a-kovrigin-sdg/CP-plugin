package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.hbsReferenceResolver.HbsReference

class HbsContributorTest : BaseContributorTest() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("dating-web/public/app/widgets/chat/profile/main.js", "// widget")
        myFixture.addFileToProject("dating-web/public/app/services/auth/service.js", "// service")
        myFixture.addFileToProject("l10n/buttons/en-US.json", "{}")
    }

    fun testWidgetLink() {
        val hbsPath = "dating-web/public/app/widgets/test_widget.hbs"

        createAndConfigure(hbsPath, """<link itemProp="widget" href="/chat/pro<caret>file" />""")

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)

        assertNotNull("Reference not found at caret", reference)
        assertTrue("Reference should be HbsReference", reference is HbsReference)

        val resolved = reference?.resolve()
        assertNotNull("Reference did not resolve", resolved)

        assertEquals("main.js", resolved!!.containingFile.name)
    }

    fun testServiceLink() {
        val hbsPath = "dating-web/public/app/widgets/test_service.hbs"
        createAndConfigure(hbsPath, """<link itemProp="service" href="/auth/ser<caret>vice" />""")

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)

        assertNotNull("Service reference not found at caret", reference)
        assertTrue(reference is HbsReference)
        assertEquals("service.js", reference!!.resolve()?.containingFile?.name)
    }

    fun testL10nLink() {
        val hbsPath = "dating-web/public/app/widgets/test_l10n.hbs"
        createAndConfigure(hbsPath, """<link itemProp="l10n" href="/butto<caret>ns" />""")

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)

        assertNotNull("L10n reference not found at caret", reference)
        assertTrue(reference is HbsReference)
        assertEquals("en-US.json", reference!!.resolve()?.containingFile?.name)
    }
}