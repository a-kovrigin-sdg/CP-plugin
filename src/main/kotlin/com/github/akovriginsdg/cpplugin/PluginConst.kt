package com.github.akovriginsdg.cpplugin

object PluginConst {
    // Базовые пути относительно корня проекта
    const val DATING_WEB_ROOT = "dating-web"
    const val APP_ROOT = "$DATING_WEB_ROOT/public/app"
    const val WIDGETS_ROOT = "$APP_ROOT/widgets"
    const val SERVICES_ROOT = "$APP_ROOT/services"
    const val L10N_ROOT = "l10n"
    const val CONFIG_PATH = "dating-web/orbit/source/modules/integrator/components/config.js"
    const val IMPORT_BASE_PATH = "dating-web/orbit/source"

    val JS_TS_EXTENSIONS = listOf("", ".tsx", ".ts", ".jsx", ".js", "/index.tsx", "/index.ts", "/index.jsx", "/index.js")
}