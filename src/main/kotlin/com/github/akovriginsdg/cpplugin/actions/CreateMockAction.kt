package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

open class CreateMockAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val isVisible = project != null &&
                file != null &&
                !file.isDirectory &&
                file.nameSequence.endsWith(".ts") &&
                !file.nameSequence.endsWith(".mock.ts") &&
                !file.nameSequence.endsWith(".d.ts")
        e.presentation.isEnabledAndVisible = isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val originalFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val mockName = "${originalFile.nameWithoutExtension}.mock.ts"
        val existingMockFile = originalFile.parent?.findChild(mockName)

        if (existingMockFile != null) {
            FileDocumentManager.getInstance().saveAllDocuments()
            val psiManager = PsiManager.getInstance(project)
            val originalPsiFile = psiManager.findFile(originalFile) ?: return

            val exports = parsePsiExports(originalPsiFile)
            if (exports.isEmpty()) {
                FileEditorManager.getInstance(project).openFile(existingMockFile, true)
                return
            }

            val mockPsiFile = psiManager.findFile(existingMockFile)
            if (mockPsiFile != null) {
                updateMockFile(project, mockPsiFile, exports)
                FileEditorManager.getInstance(project).openFile(existingMockFile, true)
                return
            }
        }

        FileDocumentManager.getInstance().saveAllDocuments()
        val psiManager = PsiManager.getInstance(project)
        val originalPsiFile = psiManager.findFile(originalFile) ?: return

        val exports = parsePsiExports(originalPsiFile)
        if (exports.isEmpty()) {
            Messages.showInfoMessage(project, "No exported classes or functions found.", "Info")
            return
        }

        val absoluteImportPath = calculateAbsoluteImportPath(originalFile) ?: run {
            Messages.showErrorDialog(project, "Could not determine domain path.", "Error")
            return
        }

        val (importsCode, bodyCode) = generateMockContent(exports, absoluteImportPath)
        createMockFile(project, originalFile.parent, "${originalFile.nameWithoutExtension}.mock", importsCode, bodyCode)
    }

    private fun updateMockFile(project: Project, mockPsiFile: PsiFile, sourceExports: List<ExportedItem>) {
        WriteCommandAction.runWriteCommandAction(project) {
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = documentManager.getDocument(mockPsiFile) ?: return@runWriteCommandAction

            documentManager.commitDocument(document)

            for (item in sourceExports) {
                when (item) {
                    is ExportedItem.ClassItem -> {
                        val mockClassName = "${item.name}Mock"
                        val mockClassPsi = findClassInFile(mockPsiFile, mockClassName)

                        if (mockClassPsi != null) {
                            val missingCode = generateMissingMembersCode(item, mockClassPsi)
                            if (missingCode.isNotBlank()) {
                                insertCodeIntoClass(document, mockClassPsi, missingCode)
                            }
                        } else {
                            val newClassCode = if (item.parentClass == "Offering") {
                                generateOfferingMock(item)
                            } else {
                                generateStandardClassMock(item)
                            }
                            appendCodeToEnd(document, newClassCode)
                        }
                    }
                    is ExportedItem.FunctionItem -> {
                        val mockFuncName = "${item.name}Mock"
                        if (!hasVariableInFile(mockPsiFile, mockFuncName)) {
                            val newFuncCode = generateFunctionMock(item)
                            appendCodeToEnd(document, newFuncCode)
                        }
                    }
                }
            }
        }
    }

    private fun findClassInFile(file: PsiFile, className: String): JSClass? {
        return PsiTreeUtil.findChildrenOfType(file, JSClass::class.java)
            .firstOrNull { it.name == className }
    }

    private fun hasVariableInFile(file: PsiFile, varName: String): Boolean {
        return PsiTreeUtil.findChildrenOfType(file, JSVarStatement::class.java)
            .flatMap { it.variables.toList() }
            .any { it.name == varName }
    }

    private fun insertCodeIntoClass(document: Document, jsClass: JSClass, codeToInsert: String) {
        val rBraceNode = jsClass.node.findChildByType(JSTokenTypes.RBRACE)
        if (rBraceNode != null) {
            document.insertString(rBraceNode.startOffset, "\n$codeToInsert")
        }
    }

    private fun appendCodeToEnd(document: Document, code: String) {
        val textLength = document.textLength
        val prefix = if (textLength > 0) "\n\n" else ""
        document.insertString(textLength, "$prefix$code")
    }

    private fun generateMissingMembersCode(sourceItem: ExportedItem.ClassItem, mockClass: JSClass): String {
        val sb = StringBuilder()
        val existingMemberNames = mutableSetOf<String>()
        mockClass.fields.forEach { it.name?.let { n -> existingMemberNames.add(n) } }
        mockClass.functions.forEach { it.name?.let { n -> existingMemberNames.add(n) } }

        sourceItem.properties.forEach { prop ->
            if (!existingMemberNames.contains(prop.name)) {
                if (prop.isObservable) {
                    val innerType = extractGenericType(prop.type) ?: "any"
                    sb.append("    readonly ${prop.name} = new ReplaySubject<$innerType>(1)\n")
                } else {
                    sb.append("    readonly ${prop.name}: ${prop.type}\n")
                }
            }
        }
        sourceItem.methods.forEach { method ->
            if (!existingMemberNames.contains(method)) {
                sb.append("    readonly $method = jest.fn<\n")
                sb.append("        ReturnType<${sourceItem.name}['$method']>,\n")
                sb.append("        Parameters<${sourceItem.name}['$method']>\n")
                sb.append("    >()\n")
            }
        }
        return sb.toString()
    }

    // --- PARSING ---

    sealed class ExportedItem {
        data class FunctionItem(val name: String) : ExportedItem()
        data class ClassItem(
            val name: String,
            val parentClass: String?,
            val methods: List<String>,
            val properties: List<Property>,
            val constructorArgsNames: List<String>,
            val rawConstructorArgs: String
        ) : ExportedItem()
    }

    data class Property(val name: String, val type: String, val isObservable: Boolean)

    private fun parsePsiExports(psiFile: PsiFile): List<ExportedItem> {
        val items = mutableListOf<ExportedItem>()
        val children = psiFile.children
        for (element in children) {
            if (isTypeOrInterface(element)) continue
            if (element is JSAttributeListOwner && isExported(element)) {
                if (element is JSClass || element is JSFunction || element is JSVarStatement) {
                    processElement(element, items)
                }
            }
        }
        return items
    }

    private fun isTypeOrInterface(element: com.intellij.psi.PsiElement): Boolean {
        val elementType = element.node.elementType.toString()
        if (elementType.contains("TYPE_ALIAS") || elementType.contains("INTERFACE")) return true
        val className = element::class.java.simpleName
        if (className.contains("TypeAlias") || className.contains("Interface")) return true
        return false
    }

    private fun isExported(element: JSAttributeListOwner): Boolean {
        val attrs = element.attributeList
        return attrs != null && attrs.hasModifier(JSAttributeList.ModifierType.EXPORT)
    }

    private fun processElement(element: JSAttributeListOwner, items: MutableList<ExportedItem>) {
        when (element) {
            is JSClass -> items.add(parseClass(element))
            is JSFunction -> element.name?.let { items.add(ExportedItem.FunctionItem(it)) }
            is JSVarStatement -> element.variables.forEach { variable ->
                if (variable.initializer is JSFunction) variable.name?.let { items.add(ExportedItem.FunctionItem(it)) }
            }
        }
    }

    private fun parseClass(jsClass: JSClass): ExportedItem.ClassItem {
        val className = jsClass.name ?: "Unknown"
        val rawParentText = jsClass.extendsList?.members?.firstOrNull()?.text
        val parentClass = rawParentText?.substringBefore("<")?.trim()

        val methods = mutableListOf<String>()
        val properties = mutableListOf<Property>()

        if (parentClass != "Offering") {
            jsClass.fields.forEach { field ->
                if (!isStatic(field) && isPublicOrReadonly(field)) {
                    val name = field.name
                    if (name != null) {
                        val typeText = field.typeElement?.text ?: "any"
                        val isObservable = typeText.startsWith("Observable<") || typeText.contains(".Observable<")
                        properties.add(Property(name, typeText, isObservable))
                    }
                }
            }
            jsClass.functions.forEach { func ->
                if (!isStatic(func) && isPublic(func)) {
                    val name = func.name
                    if (name != null) {
                        if (func.isGetProperty) {
                            val typeText = func.returnTypeElement?.text ?: "any"
                            val isObservable = typeText.startsWith("Observable<") || typeText.contains(".Observable<")
                            properties.add(Property(name, typeText, isObservable))
                        } else if (!func.isSetProperty && !func.isConstructor) {
                            methods.add(name)
                        }
                    }
                }
            }
        }

        val (argNames, rawArgs) = parseConstructor(jsClass)
        return ExportedItem.ClassItem(className, parentClass, methods, properties, argNames, rawArgs)
    }

    private fun isStatic(element: JSAttributeListOwner): Boolean = element.attributeList?.hasModifier(JSAttributeList.ModifierType.STATIC) == true
    private fun isPublic(element: JSAttributeListOwner): Boolean = element.attributeList?.accessType == JSAttributeList.AccessType.PUBLIC
    private fun isPublicOrReadonly(field: JSField): Boolean = isPublic(field)

    private fun parseConstructor(jsClass: JSClass): Pair<List<String>, String> {
        val constructor = jsClass.constructor
        if (constructor == null) return emptyList<String>() to ""
        val params = constructor.parameterList?.parameters ?: return emptyList<String>() to ""

        val argNames = params.mapNotNull { it.name }
        val rawArgsBuilder = StringBuilder()
        params.forEachIndexed { index, param ->
            if (index > 0) rawArgsBuilder.append(", ")
            rawArgsBuilder.append("${param.name}: ${param.typeElement?.text ?: "any"}")
        }
        return argNames to rawArgsBuilder.toString()
    }

    private fun calculateAbsoluteImportPath(file: VirtualFile): String? {
        val pathParts = file.path.split("/")
        val domainIndex = pathParts.indexOf(PluginConst.DOMAIN_ROOT_DIR)
        if (domainIndex == -1) return null
        val relativePath = pathParts.drop(domainIndex + 1).joinToString("/")
        var pathNoExt = relativePath.removeSuffix(".ts")
        if (pathNoExt.endsWith("/index")) pathNoExt = pathNoExt.removeSuffix("/index")
        return "${PluginConst.DOMAIN_IMPORT_PREFIX}/$pathNoExt"
    }

    private fun generateMockContent(exports: List<ExportedItem>, importPath: String): Pair<String, String> {
        val sb = StringBuilder()
        val imports = mutableListOf<String>()

        val typeImports = exports.filterIsInstance<ExportedItem.ClassItem>().joinToString(", ") { it.name }
        val valueImports = exports.filterIsInstance<ExportedItem.FunctionItem>().joinToString(", ") { it.name }

        if (typeImports.isNotEmpty()) imports.add("import type { $typeImports } from '$importPath'")
        if (valueImports.isNotEmpty()) imports.add("import { $valueImports } from '$importPath'")

        val hasOffering = exports.filterIsInstance<ExportedItem.ClassItem>().any { it.parentClass == "Offering" }
        if (hasOffering) {
            imports.add("import { Offering } from '@sdv/commons/offering/index.mock'")
            imports.add("import type { AcceptOfferOptionsFromOffering, CanOfferFromOffering, OfferFromOffering, PostponeOfferOptionsFromOffering, RejectOfferOptionsFromOffering, StartOfferOptionsFromOffering } from '@sdv/commons/offering'")
        }

        exports.forEach { item ->
            when (item) {
                is ExportedItem.ClassItem -> {
                    if (item.parentClass == "Offering") sb.append(generateOfferingMock(item)).append("\n\n")
                    else sb.append(generateStandardClassMock(item)).append("\n\n")
                }
                is ExportedItem.FunctionItem -> sb.append(generateFunctionMock(item)).append("\n\n")
            }
        }
        return imports.joinToString("\n") to sb.toString()
    }

    private fun generateFunctionMock(item: ExportedItem.FunctionItem): String {
        return """
            const ${item.name}Mock = jest.fn<ReturnType<typeof ${item.name}>, Parameters<typeof ${item.name}>>()
            export { ${item.name}Mock as ${item.name} }
        """.trimIndent()
    }

    private fun generateOfferingMock(item: ExportedItem.ClassItem): String {
        val name = item.name
        val sb = StringBuilder()
        val mockConstructorSig = item.rawConstructorArgs
        val lambdaDefinition = if (mockConstructorSig.isBlank()) "()" else "($mockConstructorSig)"
        val constructorCallArgs = item.constructorArgsNames.joinToString(", ")

        sb.append("class ${name}Mock extends Offering<\n")
        sb.append("    OfferFromOffering<$name>,\n    StartOfferOptionsFromOffering<$name>,\n    AcceptOfferOptionsFromOffering<$name>,\n    RejectOfferOptionsFromOffering<$name>,\n    PostponeOfferOptionsFromOffering<$name>,\n    CanOfferFromOffering<$name>\n")
        sb.append("> implements Public<${name}Mock> {\n")
        sb.append("    static readonly shared = mockFunctionWithReturnValueRecreatingOnEachTest(\n")
        sb.append("        $lambdaDefinition => new ${name}Mock($constructorCallArgs),\n    )\n\n")
        sb.append("    private constructor($mockConstructorSig) {\n        super()\n    }\n")
        sb.append("}\n\n")
        sb.append("export { ${name}Mock as ${name} }")
        return sb.toString()
    }

    private fun generateStandardClassMock(item: ExportedItem.ClassItem): String {
        val sb = StringBuilder()
        val mockConstructorSig = item.rawConstructorArgs
        val lambdaDefinition = if (mockConstructorSig.isBlank()) "()" else "($mockConstructorSig)"
        val constructorCallArgs = item.constructorArgsNames.joinToString(", ")

        sb.append("class ${item.name}Mock implements Public<${item.name}> {\n")
        sb.append("    static readonly shared = mockFunctionWithReturnValueRecreatingOnEachTest(\n")
        sb.append("        $lambdaDefinition => new ${item.name}Mock($constructorCallArgs),\n    )\n\n")
        sb.append("    private constructor($mockConstructorSig) {\n        //\n    }\n\n")

        item.properties.forEach { prop ->
            if (prop.isObservable) {
                val innerType = extractGenericType(prop.type) ?: "any"
                sb.append("    readonly ${prop.name} = new ReplaySubject<$innerType>(1)\n")
            } else {
                sb.append("    readonly ${prop.name}: ${prop.type}\n")
            }
        }
        if (item.properties.isNotEmpty() && item.methods.isNotEmpty()) sb.append("\n")

        item.methods.forEach { method ->
            sb.append("    readonly $method = jest.fn<\n")
            sb.append("        ReturnType<${item.name}['$method']>,\n")
            sb.append("        Parameters<${item.name}['$method']>\n")
            sb.append("    >()\n")
        }
        sb.append("}\n\n")
        sb.append("export { ${item.name}Mock as ${item.name} }")
        return sb.toString()
    }

    private fun extractGenericType(type: String): String? {
        val start = type.indexOf("<")
        val end = type.lastIndexOf(">")
        if (start != -1 && end != -1) return type.substring(start + 1, end)
        return null
    }

    protected open fun createMockFile(
        project: Project,
        directory: VirtualFile,
        fileNameWithoutExt: String,
        importsCode: String,
        bodyCode: String
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val psiDirectory = PsiManager.getInstance(project).findDirectory(directory) ?: return@runWriteCommandAction
                psiDirectory.findFile("$fileNameWithoutExt.ts")?.delete()

                val manager = FileTemplateManager.getInstance(project)
                val template = try {
                    manager.getJ2eeTemplate(PluginConst.TPL_MOCK_FILE)
                } catch (e: Exception) {
                    manager.getTemplate(PluginConst.TPL_MOCK_FILE)
                }

                val props = Properties(manager.defaultProperties)
                props.setProperty("IMPORTS", importsCode)
                props.setProperty("CONTENT", bodyCode)

                val createdElement = FileTemplateUtil.createFromTemplate(template, fileNameWithoutExt, props, psiDirectory)
                if (createdElement is PsiFile) {
                    FileEditorManager.getInstance(project).openFile(createdElement.virtualFile, true)
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Error creating mock: ${e.message}", "Error")
            }
        }
    }
}