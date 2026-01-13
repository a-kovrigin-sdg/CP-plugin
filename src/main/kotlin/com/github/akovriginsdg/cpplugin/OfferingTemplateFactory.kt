package com.github.akovriginsdg.cpplugin

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.intellij.icons.AllIcons

class OfferingTemplateFactory : FileTemplateGroupDescriptorFactory {

    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor("CP Plugin Templates", AllIcons.Providers.Spark)

        group.addTemplate(FileTemplateDescriptor("Offering.ts", AllIcons.Actions.AddFile))

        return group
    }
}