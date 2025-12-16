package com.github.akovriginsdg.cpplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile

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

    /**
     * Превращает PSI файл в путь модуля (как он используется в require или href).
     * Пример: .../public/app/widgets/foo/main.js -> widgets/foo
     */
    fun getModulePath(file: PsiFile): String? {
        val vFile = file.virtualFile ?: return null
        val path = vFile.path

        // Проверяем, что файл внутри dating-web/public/app
        // Используем константу APP_ROOT, но нам нужен полный путь для проверки или относительный
        // Проще проверить вхождение строки "/public/app/"
        val marker = "/public/app/"
        val index = path.indexOf(marker)
        if (index == -1) return null

        // Отрезаем всё до /public/app/ включительно
        var relativePath = path.substring(index + marker.length)

        // Убираем расширение (.js, .ts, .tsx)
        if (relativePath.contains(".")) {
            relativePath = relativePath.substringBeforeLast(".")
        }

        // Убираем /index или /main или /view с конца, так как к ним обращаются по имени папки
        if (relativePath.endsWith("/index")) {
            relativePath = relativePath.removeSuffix("/index")
        } else if (relativePath.endsWith("/main")) {
            relativePath = relativePath.removeSuffix("/main")
        } else if (relativePath.endsWith("/view")) {
            // view часто импортируют как './view', но если это модуль, может быть и так
            relativePath = relativePath.removeSuffix("/view")
        }

        return relativePath
    }
}