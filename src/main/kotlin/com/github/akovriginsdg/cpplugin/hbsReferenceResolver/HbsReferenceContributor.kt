package com.github.akovriginsdg.cpplugin.hbsReferenceResolver

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import com.github.akovriginsdg.cpplugin.PluginUtils

class HbsReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val pattern = XmlPatterns.xmlAttributeValue()
            .withParent(XmlPatterns.xmlAttribute("href")
                .withParent(XmlPatterns.xmlTag().withLocalName("link")))

        registrar.registerReferenceProvider(pattern, object : PsiReferenceProvider() {
            override fun getReferencesByElement(
                element: PsiElement,
                context: ProcessingContext
            ): Array<PsiReference> {
                if (!PluginUtils.isTargetProject(element.project)) {
                    return PsiReference.EMPTY_ARRAY
                }

                val tag = element.parent?.parent as? XmlTag ?: return PsiReference.EMPTY_ARRAY
                val itemPropValue = tag.getAttributeValue("itemProp") ?: return PsiReference.EMPTY_ARRAY

                val cleanText = element.text.trim('"', '\'')
                val startOffsetInElement = element.text.indexOf(cleanText)
                val projectBasePath = element.project.basePath ?: return PsiReference.EMPTY_ARRAY

                val references = mutableListOf<PsiReference>()

                val rootPath = when (itemPropValue) {
                    "widget" -> "$projectBasePath/dating-web/public/app/widgets"
                    "service" -> "$projectBasePath/dating-web/public/app/services"
                    "l10n" -> "$projectBasePath/l10n"
                    else -> return PsiReference.EMPTY_ARRAY
                }

                var currentPathBuilder = rootPath
                var currentIndex = 0

                val textToParse = if (cleanText.startsWith("/")) cleanText.substring(1) else cleanText
                val initialOffset = if (cleanText.startsWith("/")) 1 else 0
                val segments = textToParse.split("/")

                for ((i, segment) in segments.withIndex()) {
                    if (segment.isEmpty()) {
                        currentIndex += 1
                        continue
                    }

                    // Для l10n убираем хэш
                    val cleanSegment = if (itemPropValue == "l10n") segment.substringBefore('#') else segment

                    // Базовый путь до текущего сегмента (это папка)
                    currentPathBuilder = "$currentPathBuilder/$cleanSegment"

                    val segmentStart = startOffsetInElement + initialOffset + currentIndex
                    val segmentEnd = segmentStart + cleanSegment.length

                    if (segmentStart > segmentEnd) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val range = TextRange(segmentStart, segmentEnd)

                    val isLastSegment = (i == segments.size - 1)

                    val fileCandidates = mutableListOf<String>()
                    var fallbackPath: String? = currentPathBuilder

                    if (isLastSegment) {
                        when (itemPropValue) {
                            "widget", "service" -> {
                                fileCandidates.add("$currentPathBuilder.js")
                                fileCandidates.add("$currentPathBuilder/main.js")
                            }
                            "l10n" -> {
                                fileCandidates.add("$currentPathBuilder/en-US.json")
                            }
                        }
                    }

                    references.add(HbsReference(element, range, fileCandidates, fallbackPath))

                    currentIndex += segment.length + 1
                }

                return references.toTypedArray()
            }
        })
    }
}