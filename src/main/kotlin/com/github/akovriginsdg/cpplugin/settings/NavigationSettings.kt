package com.github.akovriginsdg.cpplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
@State(
    name = "CPPluginNavigationSettings",
    storages = [Storage("CPPluginSettings.xml")]
)
class NavigationSettings : PersistentStateComponent<NavigationSettings.State> {

    data class State(
        var jumpLinesCount: Int = 5
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    // Геттер/Сеттер для удобного доступа
    var jumpLinesCount: Int
        get() = myState.jumpLinesCount
        set(value) {
            myState.jumpLinesCount = value
        }

    companion object {
        val instance: NavigationSettings
            get() = ApplicationManager.getApplication().getService(NavigationSettings::class.java)
    }
}