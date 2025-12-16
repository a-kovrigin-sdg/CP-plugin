package com.github.akovriginsdg.cpplugin

import com.github.akovriginsdg.cpplugin.fluxReferenceResolver.FluxComponentReference
import com.intellij.psi.PsiReference

class FluxReferenceTest : BasePluginTest() {

    override fun setUp() {
        super.setUp()
        addFile("${PluginConst.IMPORT_BASE_PATH}/components/profile/index.js", "")
        addFile(PluginConst.CONFIG_PATH, """
            var componentsConfig = { 'user-profile': function() { return import('components/profile/index'); } };
        """.trimIndent())
    }

    fun `test flux event reference`() {
        // JS тесты требуют полного пути, чтобы работать корректно
        val path = "${PluginConst.WIDGETS_ROOT}/test/flux_test.js"

        addFile(path, """
            bus.emit('command.flux.component.render', 'user-pr<caret>ofile', data);
        """.trimIndent())

        myFixture.configureFromTempProjectFile(path)

        var element = myFixture.file.findElementAt(myFixture.caretOffset)
        var reference: PsiReference? = null

        while (element != null && reference == null) {
            reference = element.references.find { it is FluxComponentReference }
            element = element.parent
            if (element is com.intellij.psi.PsiFile) break
        }

        assertNotNull("FluxComponentReference not found", reference)
        assertEquals("index.js", reference!!.resolve()?.containingFile?.name)
    }
}