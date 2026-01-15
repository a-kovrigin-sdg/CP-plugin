package com.github.akovriginsdg.cpplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class NavigationConfigurable : Configurable {

    private var myJumpLinesSpinner: JBIntSpinner? = null

    // Название пункта в меню настроек
    override fun getDisplayName(): String = "CP Helper Plugin"

    override fun createComponent(): JComponent {
        // Создаем спиннер (числовое поле) с диапазоном от 1 до 100
        myJumpLinesSpinner = JBIntSpinner(5, 1, 100)

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Lines to jump:", myJumpLinesSpinner!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    // Проверяем, изменил ли пользователь значение
    override fun isModified(): Boolean {
        val settings = NavigationSettings.instance
        return myJumpLinesSpinner!!.number != settings.jumpLinesCount
    }

    // Сохраняем изменения (при нажатии Apply/OK)
    override fun apply() {
        val settings = NavigationSettings.instance
        settings.jumpLinesCount = myJumpLinesSpinner!!.number
    }

    // Сбрасываем UI к сохраненному значению (при открытии настроек или нажатии Reset)
    override fun reset() {
        val settings = NavigationSettings.instance
        myJumpLinesSpinner!!.number = settings.jumpLinesCount
    }

    override fun disposeUIResources() {
        myJumpLinesSpinner = null
    }
}