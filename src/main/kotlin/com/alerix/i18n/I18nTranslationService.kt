package com.alerix.i18n

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class I18nTranslationService(private val project: Project) {
    private val cache = ConcurrentHashMap<String, CachedJson>()

    fun listLanguages(settings: I18nSettingsState): List<String> {
        val baseDir = project.basePath ?: return emptyList()
        val localesDir = Paths.get(baseDir, settings.localesPath).toString()
        val baseVfs = LocalFileSystem.getInstance().findFileByPath(localesDir) ?: return emptyList()
        return baseVfs.children
            .filter { it.isDirectory }
            .map { it.name }
            .sorted()
    }

    fun resolveTranslation(
        settings: I18nSettingsState,
        namespace: String,
        key: String,
        language: String,
    ): String? {
        val json = loadNamespaceJson(settings, namespace, language) ?: return null
        return resolveJsonPath(json, key)
    }

    private fun loadNamespaceJson(
        settings: I18nSettingsState,
        namespace: String,
        language: String,
    ): JsonObject? {
        val baseDir = project.basePath ?: return null
        val jsonPath = Paths.get(baseDir, settings.localesPath, language, "$namespace.json").toString()
        val vfs = LocalFileSystem.getInstance().findFileByPath(jsonPath) ?: return null
        val stamp = vfs.modificationStamp
        val cached = cache[jsonPath]
        if (cached != null && cached.modStamp == stamp) {
            return cached.json
        }
        val psiFile = PsiManager.getInstance(project).findFile(vfs) as? JsonFile ?: return null
        val root = psiFile.topLevelValue as? JsonObject ?: return null
        cache[jsonPath] = CachedJson(stamp, root)
        return root
    }

    private fun resolveJsonPath(root: JsonObject, key: String): String? {
        val parts = key.split('.')
        var current: Any? = root
        for (part in parts) {
            val obj = current as? JsonObject ?: return null
            val property = obj.findProperty(part) ?: return null
            current = property.value
        }
        return when (current) {
            is JsonStringLiteral -> current.value
            is JsonValue -> current.text.trim()
            null -> null
            else -> current.toString().trim()
        }
    }

    private data class CachedJson(val modStamp: Long, val json: JsonObject)
}
