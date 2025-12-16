package com.github.akovriginsdg.cpplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
 import com.intellij.openapi.project.guessProjectDir
 import com.intellij.openapi.vfs.VfsUtil

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
        val project = file.project
        val vFile = file.virtualFile ?: return null
        val baseDir = project.guessProjectDir() ?: return null

        // 1. Получаем путь относительно корня проекта (надежно работает и в тестах)
        // Пример: "dating-web/public/app/modules/dialogs/chat.js"
        val pathFromRoot = VfsUtil.getRelativePath(vFile, baseDir) ?: return null

        val appRoot = PluginConst.APP_ROOT // "dating-web/public/app"

        // 2. Проверяем, что файл внутри нашего app
        if (!pathFromRoot.startsWith(appRoot)) return null

        // 3. Формируем путь модуля
        // "modules/dialogs/chat.js"
        var modulePath = pathFromRoot.removePrefix(appRoot).removePrefix("/")

        // Убираем расширение
        if (modulePath.contains(".")) {
            modulePath = modulePath.substringBeforeLast(".")
        }

        // Убираем стандартные имена файлов (как в requirejs логике)
        if (modulePath.endsWith("/index")) {
            modulePath = modulePath.removeSuffix("/index")
        } else if (modulePath.endsWith("/main")) {
            modulePath = modulePath.removeSuffix("/main")
        } else if (modulePath.endsWith("/view")) {
            modulePath = modulePath.removeSuffix("/view")
        }

        return modulePath
    }
}