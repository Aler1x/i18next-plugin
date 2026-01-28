package com.alerix.i18n.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "I18nSettings", storages = [Storage("i18n-plugin.xml")])
class I18nSettingsService : PersistentStateComponent<I18nSettingsState> {
    private var state = I18nSettingsState()

    override fun getState(): I18nSettingsState = state

    override fun loadState(state: I18nSettingsState) {
        this.state = state
    }
}
