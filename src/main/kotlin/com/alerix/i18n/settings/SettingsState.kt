package com.alerix.i18n.settings

data class SettingsState(
    var localesPath: String = "public/locales",
    var defaultNamespace: String = "common",
    var inlineLanguage: String = "en",
)
