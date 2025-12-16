package com.github.akovriginsdg.cpplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

object PluginUtils {
    /**
     * Проверяет, является ли текущий проект целевым для плагина.
     * Критерий: наличие папки "dating-web" в корне.
     */
    fun isTargetProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val markerPath = "$basePath/${PluginConst.DATING_WEB_ROOT}"
        val markerFile = LocalFileSystem.getInstance().findFileByPath(markerPath)

        return markerFile != null && markerFile.isDirectory
    }

    /**
     * Конвертирует PascalCase или camelCase в kebab-case.
     * Пример: UserProfile -> user-profile
     */
    fun toKebabCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z]+)"), "$1-$2").lowercase()
    }
}