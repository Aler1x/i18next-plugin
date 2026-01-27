package com.alerix.i18n

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class I18nDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val call = findI18nCall(originalElement) ?: return null
        val project = element?.project ?: return null

        val settingsService = service<I18nSettingsService>()
        val translationService = project.service<I18nTranslationService>()

        val callInfo = parseCall(call, settingsService.state) ?: return null
        val languages = translationService.listLanguages(settingsService.state)

        if (languages.isEmpty()) return null

        val lines = mutableListOf<String>()
        lines.add("<html><body>")
        lines.add("<b>i18next Translations</b><br/>")
        lines.add("<b>Key:</b> ${callInfo.key}<br/>")
        lines.add("<b>Namespace:</b> ${callInfo.namespace}<br/><br/>")

        for (lang in languages) {
            val value = translationService.resolveTranslation(
                settingsService.state,
                callInfo.namespace,
                callInfo.key,
                lang,
            )
            val displayValue = if (value != null) {
                escapeHtml(truncate(value))
            } else {
                "<i>&lt;missing&gt;</i>"
            }
            lines.add("<b>$lang:</b> $displayValue<br/>")
        }

        lines.add("</body></html>")
        return lines.joinToString("")
    }

    private fun findI18nCall(element: PsiElement?): JSCallExpression? {
        var current = element
        while (current != null) {
            if (current is JSCallExpression) {
                val calleeText = current.methodExpression?.text
                if (calleeText == "t" || calleeText?.endsWith(".t") == true) {
                    return current
                }
            }
            current = current.parent
        }
        return null
    }

    private fun parseCall(call: JSCallExpression, settings: I18nSettingsState): CallInfo? {
        val calleeText = call.methodExpression?.text ?: return null
        if (calleeText != "t" && !calleeText.endsWith(".t")) {
            return null
        }
        val args = call.arguments
        val firstArg = args.firstOrNull() ?: return null
        val key = parseArrowKey(firstArg.text) ?: return null
        val namespace = parseNamespace(args.getOrNull(1)?.text)
            ?: findUseTranslationNamespace(call)
            ?: settings.defaultNamespace
        return CallInfo(key, namespace)
    }

    private fun parseArrowKey(text: String): String? {
        val match = ARROW_REGEX.find(text) ?: return null
        val paramName = match.groupValues[1]
        var bodyText = match.groupValues[2].trim()
        if (bodyText.startsWith("{")) {
            val returnMatch = RETURN_REGEX.find(bodyText) ?: return null
            bodyText = returnMatch.groupValues[1].trim()
        }
        return parseKeyFromBody(bodyText, paramName)
    }

    private fun parseKeyFromBody(bodyText: String, paramName: String): String? {
        var i = 0
        val text = bodyText.trim()
        if (!text.startsWith(paramName)) {
            return null
        }
        i += paramName.length
        val parts = mutableListOf<String>()
        while (i < text.length) {
            i = skipWhitespace(text, i)
            when {
                i < text.length && text[i] == '.' -> {
                    i++
                    val start = i
                    while (i < text.length && isIdentChar(text[i])) {
                        i++
                    }
                    if (start == i) return null
                    parts.add(text.substring(start, i))
                }
                i < text.length && text[i] == '[' -> {
                    val close = parseBracketString(text, i) ?: return null
                    parts.add(close.value)
                    i = close.endIndex
                }
                else -> break
            }
        }
        if (parts.isEmpty()) return null
        i = skipWhitespace(text, i)
        if (i != text.length) return null
        return parts.joinToString(".")
    }

    private fun parseNamespace(text: String?): String? {
        if (text == null) return null
        val match = NS_REGEX.find(text) ?: return null
        return match.groupValues[1]
    }

    private fun findUseTranslationNamespace(call: JSCallExpression): String? {
        val fileText = call.containingFile.text ?: return null
        val offset = call.textRange.startOffset
        if (offset <= 0) return null
        val prefix = fileText.substring(0, offset.coerceAtMost(fileText.length))
        val match = USE_TRANSLATION_REGEX.findAll(prefix).lastOrNull() ?: return null
        val argsText = match.groupValues[1]
        return parseUseTranslationArgs(argsText)
    }

    private fun parseUseTranslationArgs(argsText: String): String? {
        val text = argsText.trim()
        val match = STRING_LITERAL_REGEX.find(text) ?: return null
        return match.groupValues[1]
    }

    private fun parseBracketString(text: String, openIndex: Int): BracketString? {
        var i = openIndex
        if (text[i] != '[') return null
        i++
        i = skipWhitespace(text, i)
        if (i >= text.length) return null
        val quote = text[i]
        if (quote != '"' && quote != '\'') return null
        i++
        val start = i
        while (i < text.length && text[i] != quote) {
            i++
        }
        if (i >= text.length) return null
        val value = text.substring(start, i)
        i++
        i = skipWhitespace(text, i)
        if (i >= text.length || text[i] != ']') return null
        i++
        return BracketString(value, i)
    }

    private fun skipWhitespace(text: String, index: Int): Int {
        var i = index
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }
        return i
    }

    private fun isIdentChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '$'
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

    private data class CallInfo(val key: String, val namespace: String)
    private data class BracketString(val value: String, val endIndex: Int)

    private companion object {
        private val ARROW_REGEX =
            Regex("""^\s*\(?\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*\)?\s*=>\s*(.+)$""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val RETURN_REGEX =
            Regex("""\breturn\s+([^;]+);""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val NS_REGEX =
            Regex("""\bns\s*:\s*['"]([^'"]+)['"]""")
        private val USE_TRANSLATION_REGEX =
            Regex(
                """\b(?:const|let|var)\s*\{[^}]*\bt\b[^}]*}\s*=\s*useTranslation\s*\(([^)]*)\)""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
        private val STRING_LITERAL_REGEX =
            Regex("""['"]([^'"]+)['"]""")
    }
}
