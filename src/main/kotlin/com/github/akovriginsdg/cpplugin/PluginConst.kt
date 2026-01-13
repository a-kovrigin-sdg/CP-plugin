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

    // --- ШАБЛОНЫ REACT  ---
    val TPL_INDEX = """
        import { memo } from 'react'
        import View from './view'

        type Props = {
            // Define your props here
        }

        export const %s = memo((props: Props) => {
            // const {} = props
  
            return (
                <View 
                    {...props} 
                />
            )
        })
    """.trimIndent()

    val TPL_VIEW = """
        import { memo } from 'react'
        import styles from './styles.module.less'

        type Props = {
            // Define your props here
        }

        export default memo((props: Props) => {
            // const {} = props
            
            return (
                <div className={styles.container}>
                    // 
                </div>
            )
        })
    """.trimIndent()

    val TPL_STYLE = """
        .container {
            display: block;
        }
    """.trimIndent()

    val TPL_TRACKING = """
        import type { ComponentPropsType, ComponentType } from 'react'
        import { memo } from 'react'
        import type View from './view'

        type ViewProps = ComponentPropsType<typeof View>

        type Props = ViewProps & {
            // Add tracking props here
        }

        export default (Component: ComponentType<ViewProps>) => memo((props: Props) => {
            const { ...otherProps } = props

            return <Component {...otherProps} />
        })
    """.trimIndent()

    // --- ШАБЛОНЫ DOMAIN INJECTION ---

    val TPL_DOMAIN_AGGREGATOR = """
        export * from '%s/%s/.injected'
        
        export * from './%s.contracts'
        
    """.trimIndent()

    val TPL_DOMAIN_CONTRACTS_CLASS = """
        export type %s = {
            // TODO: Define interface
        }
        
    """.trimIndent()

    val TPL_DOMAIN_INJECTION_TARGET_CLASS = """
        import type { %s } from '%s/%s'
        import { singleton } from '@sdv/commons/utils/singleton'

        class %sImplementation implements %s {
            static readonly shared = singleton((userId: string) => new %sImplementation(userId))
        
            private readonly userId: string
        
            private constructor(userId: string) {
                this.userId = userId
                throw new Error('Not implemented')
            }
        }
        
        export { %sImplementation as %s }
        
    """.trimIndent()

    // 4. FUNCTION: Контракт
    val TPL_DOMAIN_CONTRACTS_FUNCTION = """
        import { Single } from '@sdv/commons/rx/single'

        export type %s = () => Single<void>
        
    """.trimIndent()

    // 5. FUNCTION: Реализация
    val TPL_DOMAIN_INJECTION_TARGET_FUNCTION = """
        import { Single } from '@sdv/commons/rx/single'
        import type { %s } from '%s/%s'

        const %sImplementation: %s = () => {
            return new Single.error('Not implemented')
        }

        export { %sImplementation as %s }
        
    """.trimIndent()
}