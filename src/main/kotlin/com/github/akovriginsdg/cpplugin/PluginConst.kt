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

    // Шаблоны для генерации компонента
    // %s будет заменено на имя компонента (например, MyComponent)

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
}