package com.alerix.i18n.documentation

import com.alerix.i18n.CallInfo
import com.alerix.i18n.CallParser
import com.alerix.i18n.TranslationService
import com.alerix.i18n.settings.SettingsService
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement

class DocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val project = element?.project ?: return null
        val settingsService = service<SettingsService>()
        val translationService = project.service<TranslationService>()

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
                "${escapeHtml(truncate(value))}"
            } else {
                "<i>&lt;missing&gt;</i>"
            }
            lines.add("<b>$lang:</b> $displayValue<br/>")
        }

        lines.add("</body></html>")
        return lines.joinToString("")
    }

    private fun findCallInfo(element: PsiElement?, settingsService: SettingsService): CallInfo? {
        if (element == null) return null

        val tCall = CallParser.findI18nCall(element)
        if (tCall != null) {
            return CallParser.parseCall(tCall, settingsService.state)
        }

        val transTag = CallParser.findTransComponent(element)
        if (transTag != null) {
            return CallParser.parseTransComponent(transTag, settingsService.state)
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
