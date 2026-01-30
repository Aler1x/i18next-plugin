package com.alerix.i18n.inline

import com.alerix.i18n.CallInfo
import com.alerix.i18n.CallParser
import com.alerix.i18n.TranslationService
import com.alerix.i18n.settings.SettingsService
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import javax.swing.JComponent
import javax.swing.JPanel

class InlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val key: SettingsKey<NoSettings> = SettingsKey("i18n.inlay.hints")
    override val name: String = "i18next translations"
    override val previewText: String = "i18next.t(\$ => \$.myKey) /*<# inline language #>*/"

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: com.intellij.codeInsight.hints.ChangeListener): JComponent {
                return JPanel()
            }
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector {
        val project = file.project
        val translationService = project.service<TranslationService>()
        val settingsService = service<SettingsService>()
        return object : FactoryInlayHintsCollector(editor) {
            private val processedOffsets = mutableSetOf<Int>()

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val callInfo: CallInfo?
                val offset: Int

                when (element) {
                    is JSCallExpression -> {
                        callInfo = CallParser.parseCall(element, settingsService.state)
                        val firstArg = element.arguments.firstOrNull()
                        offset = firstArg?.textRange?.endOffset ?: element.textRange.endOffset
                    }
                    is XmlTag -> {
                        if (element.name != "Trans") return true
                        callInfo = CallParser.parseTransComponent(element, settingsService.state)
                        val i18nKeyAttr = element.getAttribute("i18nKey")
                        offset = i18nKeyAttr?.valueElement?.textRange?.endOffset ?: element.textRange.endOffset
                    }
                    else -> return true
                }

                if (callInfo == null) return true
                if (offset in processedOffsets) return true

                val caretOffset = editor.caretModel.offset
                if (caretOffset in callInfo.textRange.startOffset..callInfo.textRange.endOffset) return true

                val inlineLang = settingsService.state.inlineLanguage
                val result = translationService.resolveTranslationWithNamespace(
                    settingsService.state,
                    callInfo.namespaces,
                    callInfo.key,
                    inlineLang,
                )
                processedOffsets.add(offset)
                val presentation = if (result != null) {
                    val (_, inlineValue) = result
                    factory.roundWithBackground(factory.smallText(truncate(inlineValue)))
                } else {
                    factory.roundWithBackground(factory.smallText("[missing]"))
                }
                sink.addBlockElement(
                    offset,           // line offset; block is drawn at line start, offset only identifies the line
                    true,            // relatesToPrecedingText: hint is associated with the code at this offset
                    true,            // showAbove: draw above the line (false = below)
                    0,               // priority: lower = higher in stacking when multiple block inlays on same line
                    presentation,
                )
                return true
            }
        }
    }

    private fun truncate(value: String, max: Int = 120): String {
        if (value.length <= max) return value
        return value.substring(0, max - 3) + "..."
    }
}
