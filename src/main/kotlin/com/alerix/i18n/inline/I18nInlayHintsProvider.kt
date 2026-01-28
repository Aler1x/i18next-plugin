package com.alerix.i18n.inline

import com.alerix.i18n.I18nCallInfo
import com.alerix.i18n.I18nCallParser
import com.alerix.i18n.I18nTranslationService
import com.alerix.i18n.settings.I18nSettingsService
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

class I18nInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val key: SettingsKey<NoSettings> = SettingsKey("i18n.inlay.hints")
    override val name: String = "i18next translations"
    override val previewText: String = "i18next.t(\$ => \$.myKey)"

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
        val translationService = project.service<I18nTranslationService>()
        val settingsService = service<I18nSettingsService>()
        return object : FactoryInlayHintsCollector(editor) {
            private val processedOffsets = mutableSetOf<Int>()

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val callInfo: I18nCallInfo?
                val offset: Int

                when (element) {
                    is JSCallExpression -> {
                        callInfo = I18nCallParser.parseCall(element, settingsService.state)
                        offset = element.textRange.endOffset
                    }
                    is XmlTag -> {
                        if (element.name != "Trans") return true
                        callInfo = I18nCallParser.parseTransComponent(element, settingsService.state)
                        offset = element.textRange.endOffset
                    }
                    else -> return true
                }

                if (callInfo == null) return true
                if (offset in processedOffsets) return true

                val inlineLang = settingsService.state.inlineLanguage
                val result = translationService.resolveTranslationWithNamespace(
                    settingsService.state,
                    callInfo.namespaces,
                    callInfo.key,
                    inlineLang,
                )
                if (result != null) {
                    processedOffsets.add(offset)
                    val (_, inlineValue) = result
                    val text = "i18n[$inlineLang]: ${truncate(inlineValue)}"
                    val presentation = factory.smallText(text)
                    sink.addInlineElement(
                        offset,
                        true,
                        presentation,
                        false,
                    )
                }
                return true
            }
        }
    }

    private fun truncate(value: String, max: Int = 120): String {
        if (value.length <= max) return value
        return value.substring(0, max - 3) + "..."
    }
}
