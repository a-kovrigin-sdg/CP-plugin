package com.github.akovriginsdg.cpplugin

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.intellij.icons.AllIcons

class OfferingTemplateFactory : FileTemplateGroupDescriptorFactory {

    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor("CP Plugin Templates", AllIcons.Providers.Spark)

        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_DOMAIN_OFFERING, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_DOMAIN_AGGREGATOR, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_DOMAIN_CONTRACTS_CLASS, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_DOMAIN_CONTRACTS_FUNCTION, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_DOMAIN_IMPL_CLASS, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_DOMAIN_IMPL_FUNCTION, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_REACT_INDEX, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_REACT_VIEW, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_REACT_STYLE, AllIcons.FileTypes.UiForm))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_TRACKING, AllIcons.Actions.AddFile))
        group.addTemplate(FileTemplateDescriptor(PluginConst.TPL_MOCK_FILE, AllIcons.Actions.AddFile))
        return group
    }
}