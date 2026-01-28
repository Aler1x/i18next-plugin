package com.alerix.i18n

import com.alerix.i18n.settings.SettingsState
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

data class CallInfo(
    val key: String,
    val namespaces: List<String>,
    val textRange: com.intellij.openapi.util.TextRange,
)

object CallParser {
    fun findI18nCall(element: PsiElement?): JSCallExpression? {
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

    fun findTransComponent(element: PsiElement?): XmlTag? {
        var current = element
        while (current != null) {
            if (current is XmlTag && current.name == "Trans") {
                return current
            }
            current = current.parent
        }
        return null
    }

    fun parseCall(call: JSCallExpression, settings: SettingsState): CallInfo? {
        val calleeText = call.methodExpression?.text ?: return null
        if (calleeText != "t" && !calleeText.endsWith(".t")) {
            return null
        }
        val args = call.arguments
        val firstArg = args.firstOrNull() ?: return null
        val key = parseArrowKey(firstArg.text) ?: return null
        val explicitNs = parseNamespace(args.getOrNull(1)?.text)
        val namespaces = if (explicitNs != null) {
            listOf(explicitNs)
        } else {
            findContextNamespaces(call) ?: listOf(settings.defaultNamespace)
        }
        return CallInfo(key, namespaces, call.textRange)
    }

    fun parseTransComponent(tag: XmlTag, settings: SettingsState): CallInfo? {
        val i18nKeyAttr = tag.getAttribute("i18nKey") ?: return null
        val rawKey = i18nKeyAttr.value ?: return null

        val (key, explicitNs) = parseI18nKeyWithNamespace(rawKey)
        val namespaces = if (explicitNs != null) {
            listOf(explicitNs)
        } else {
            findContextNamespaces(tag) ?: listOf(settings.defaultNamespace)
        }
        return CallInfo(key, namespaces, tag.textRange)
    }

    private fun parseI18nKeyWithNamespace(rawKey: String): Pair<String, String?> {
        val colonIndex = rawKey.indexOf(':')
        return if (colonIndex > 0) {
            val ns = rawKey.substring(0, colonIndex)
            val key = rawKey.substring(colonIndex + 1)
            key to ns
        } else {
            rawKey to null
        }
    }

    fun parseArrowKey(text: String): String? {
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
        return parts.joinToString(".")
    }

    fun parseNamespace(text: String?): String? {
        if (text == null) return null
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return null

        // Only match ns: at depth 1 (top level of the options object)
        var depth = 0
        var i = 0
        while (i < trimmed.length) {
            when (trimmed[i]) {
                '{', '(', '[' -> depth++
                '}', ')', ']' -> depth--
                'n' -> {
                    if (depth == 1) {
                        val rest = trimmed.substring(i)
                        val match = NS_REGEX.matchAt(rest, 0)
                        if (match != null) {
                            return match.groupValues[1]
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    fun findContextNamespaces(element: PsiElement): List<String>? {
        val fileText = element.containingFile?.text ?: return null
        val offset = element.textRange?.startOffset ?: return null
        if (offset <= 0) return null
        val prefix = fileText.substring(0, offset.coerceAtMost(fileText.length))

        val useTranslationMatch = USE_TRANSLATION_REGEX.findAll(prefix).lastOrNull()
        if (useTranslationMatch != null) {
            val result = parseUseTranslationArgs(useTranslationMatch.groupValues[1])
            if (result != null) return result
        }

        val tFunctionMatch = TFUNCTION_REGEX.findAll(prefix).lastOrNull()
        if (tFunctionMatch != null) {
            val arrayContent = tFunctionMatch.groupValues[1]
            val namespaces = STRING_LITERAL_REGEX.findAll(arrayContent)
                .map { it.groupValues[1] }
                .toList()
            if (namespaces.isNotEmpty()) return namespaces
        }

        return null
    }

    private fun parseUseTranslationArgs(argsText: String): List<String>? {
        val text = argsText.trim()
        val arrayMatch = ARRAY_LITERAL_REGEX.find(text)
        if (arrayMatch != null) {
            val arrayContent = arrayMatch.groupValues[1]
            val namespaces = STRING_LITERAL_REGEX.findAll(arrayContent)
                .map { it.groupValues[1] }
                .toList()
            return namespaces.ifEmpty { null }
        }
        val match = STRING_LITERAL_REGEX.find(text) ?: return null
        return listOf(match.groupValues[1])
    }

    private fun parseBracketString(text: String, openIndex: Int): BracketString? {
        var i = openIndex
        if (text[i] != '[') return null
        i++
        i = skipWhitespace(text, i)
        if (i >= text.length) return null
        val quote = text[i]
        if (quote != '"' && quote != '\'' && quote != '`') return null
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
        return c.isLetterOrDigit() || c == '_' || c == '$' || c == '-'
    }

    private data class BracketString(val value: String, val endIndex: Int)

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
    private val ARRAY_LITERAL_REGEX =
        Regex("""\[([^\]]+)]""")
    private val TFUNCTION_REGEX =
        Regex("""\bt\s*:\s*TFunction\s*<\s*\[([^\]]+)]""", setOf(RegexOption.IGNORE_CASE))
}
