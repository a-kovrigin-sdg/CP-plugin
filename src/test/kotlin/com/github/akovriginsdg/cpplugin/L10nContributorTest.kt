package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.l10nReferenceResolver.L10nReference

class L10nContributorTest : BaseContributorTest() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("dating-web/l10n/shared/common/en-US.json", "{}")
        myFixture.addFileToProject("l10n/shared/common/en-US.json", "{}")
    }

    fun testXmlAttributeLink() {
        val xmlPath = "dating-web/public/app/widgets/test_l10n.xml"
        createAndConfigure(xmlPath, """<LocalizedText href="/shared/comm<caret>on#save" />""")

        val element = myFixture.file.findElementAt(myFixture.caretOffset)?.parent
        val reference = element?.references?.find { it is L10nReference }

        assertNotNull("L10n XML reference not found", reference)
        assertEquals("en-US.json", reference!!.resolve()?.containingFile?.name)
    }
}