package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.l10nReferenceResolver.L10nReference

class L10nReferenceTest : BasePluginTest() {

    override fun setUp() {
        super.setUp()
        addFile("l10n/shared/buttons/en-US.json", "{}")
    }

    fun `test l10n reference in XML attribute`() {
        myFixture.configureByText("test.xml", """<LocalizedText href="/shared/butt<caret>ons#save" />""")

        val element = myFixture.file.findElementAt(myFixture.caretOffset)?.parent
        val reference = element?.references?.find { it is L10nReference }

        assertNotNull("L10nReference not found", reference)
        assertEquals("en-US.json", reference!!.resolve()?.containingFile?.name)
    }
}