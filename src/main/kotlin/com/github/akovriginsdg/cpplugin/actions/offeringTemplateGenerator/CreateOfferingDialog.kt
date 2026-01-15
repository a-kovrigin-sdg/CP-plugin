package com.github.akovriginsdg.cpplugin.actions.offeringTemplateGenerator

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class CreateOfferingDialog(project: Project?) : DialogWrapper(project) {

    val nameField = JBTextField()
    val shortNameCheckbox = JBCheckBox("Use short file name (offering.ts)", true) // true = выбрано по умолчанию

    init {
        title = "Create New Offering"
        init()
    }

    override fun createCenterPanel(): JComponent? {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Class name:", nameField)
            .addComponent(shortNameCheckbox)
            .addTooltip("If checked, file will be named 'offering.ts'. Otherwise 'kebab-case-offering.ts'")
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return nameField
    }
}