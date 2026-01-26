package com.alerix.i18n

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.openapi.components.service

class I18nSettingsConfigurable : BoundConfigurable("I18next Inlay Hints") {
    private val settings = service<I18nSettingsService>()

    override fun createPanel(): DialogPanel {
        val state = settings.state
        return panel {
            row("Locales base path") {
                textField()
                    .bindText(state::localesPath)
                    .comment("Relative to project root, e.g. public/locales")
            }
            row("Default namespace") {
                textField()
                    .bindText(state::defaultNamespace)
                    .comment("Used when no ns option is provided in t(...)")
            }
            row("Inline language") {
                textField()
                    .bindText(state::inlineLanguage)
                    .comment("Language used for inline hints, e.g. en")
            }
            row {
                checkBox("Show all languages above call")
                    .bindSelected(state::showAllLanguages)
            }
        }
    }
}
