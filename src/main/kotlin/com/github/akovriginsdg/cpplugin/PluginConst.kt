package com.github.akovriginsdg.cpplugin

object PluginConst {
    // Базовые пути относительно корня проекта
    const val DATING_WEB_ROOT = "dating-web"
    const val DATING_MOBILE_ROOT = "dating-mobile"

    const val APP_ROOT = "$DATING_WEB_ROOT/public/app"
    const val WIDGETS_ROOT = "$APP_ROOT/widgets"
    const val SERVICES_ROOT = "$APP_ROOT/services"
    const val L10N_ROOT = "l10n"
    const val CONFIG_PATH = "dating-web/orbit/source/modules/integrator/components/config.js"
    const val IMPORT_BASE_PATH = "dating-web/orbit/source"
    const val WEB_COMPONENTS_FOLDER = "$DATING_WEB_ROOT/orbit/source/components"

    // Domain Injection
    const val DOMAIN_ROOT_DIR = "domain"
    const val WEB_DOMAIN_PATH = "$IMPORT_BASE_PATH/$DOMAIN_ROOT_DIR"
    const val MOBILE_DOMAIN_PATH = "$DATING_MOBILE_ROOT/src/$DOMAIN_ROOT_DIR"
    const val DOMAIN_IMPORT_PREFIX = "@sdv/domain"

    val JS_TS_EXTENSIONS = listOf("", ".tsx", ".ts", ".jsx", ".js", "/index.tsx", "/index.ts", "/index.jsx", "/index.js")

    const val TEMPLATE_OFFERING = "Offering"
    const val TPL_DOMAIN_OFFERING = "Offering.ts"
    const val TPL_DOMAIN_AGGREGATOR = "DomainAggregator.ts"
    const val TPL_DOMAIN_CONTRACTS_CLASS = "DomainContractsClass.ts"
    const val TPL_DOMAIN_CONTRACTS_FUNCTION = "DomainContractsFunction.ts"
    const val TPL_DOMAIN_IMPL_CLASS = "DomainImplementationClass.ts"
    const val TPL_DOMAIN_IMPL_FUNCTION = "DomainImplementationFunction.ts"
    const val TPL_REACT_INDEX = "ReactComponentIndex.tsx"
    const val TPL_REACT_VIEW = "ReactComponentView.tsx"
    const val TPL_REACT_STYLE = "ReactComponentStyle.less"
    const val TPL_TRACKING = "ReactTracking.tsx"
}