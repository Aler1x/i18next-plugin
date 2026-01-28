package com.alerix.i18n.documentation

import com.alerix.i18n.I18nCallInfo
import com.alerix.i18n.I18nCallParser
import com.alerix.i18n.I18nTranslationService
import com.alerix.i18n.settings.I18nSettingsService
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement

class I18nDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val project = element?.project ?: return null
        val settingsService = service<I18nSettingsService>()
        val translationService = project.service<I18nTranslationService>()

        val callInfo = findCallInfo(originalElement, settingsService) ?: return null
        val languages = translationService.listLanguages(settingsService.state)
        if (languages.isEmpty()) return null

        val foundNamespaces = translationService.findNamespacesContainingKey(
            settingsService.state,
            callInfo.namespaces,
            callInfo.key,
        )

        val displayNamespaces = foundNamespaces.ifEmpty { callInfo.namespaces }

        val lines = mutableListOf<String>()
        lines.add("<html><body>")
        lines.add("<b>i18next Translations</b><br/>")
        lines.add("<b>Key:</b> ${escapeHtml(callInfo.key)}<br/>")
        lines.add("<b>Namespaces:</b> ${displayNamespaces.joinToString(", ")}<br/><br/>")

        for (lang in languages) {
            val result = translationService.resolveTranslationWithNamespace(
                settingsService.state,
                callInfo.namespaces,
                callInfo.key,
                lang,
            )
            val displayValue = if (result != null) {
                val (ns, value) = result
                val nsIndicator = if (callInfo.namespaces.size > 1) " <i>($ns)</i>" else ""
                "${escapeHtml(truncate(value))}$nsIndicator"
            } else {
                "<i>&lt;missing&gt;</i>"
            }
            lines.add("<b>$lang:</b> $displayValue<br/>")
        }

        lines.add("</body></html>")
        return lines.joinToString("")
    }

    private fun findCallInfo(element: PsiElement?, settingsService: I18nSettingsService): I18nCallInfo? {
        if (element == null) return null

        val tCall = I18nCallParser.findI18nCall(element)
        if (tCall != null) {
            return I18nCallParser.parseCall(tCall, settingsService.state)
        }

        val transTag = I18nCallParser.findTransComponent(element)
        if (transTag != null) {
            return I18nCallParser.parseTransComponent(transTag, settingsService.state)
        }

        return null
    }

    private fun truncate(value: String, max: Int = 200): String {
        if (value.length <= max) return value
        return value.substring(0, max - 3) + "..."
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
