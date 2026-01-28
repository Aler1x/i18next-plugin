package com.alerix.i18n.settings

data class I18nSettingsState(
    var localesPath: String = "public/locales",
    var defaultNamespace: String = "common",
    var inlineLanguage: String = "en",
)
