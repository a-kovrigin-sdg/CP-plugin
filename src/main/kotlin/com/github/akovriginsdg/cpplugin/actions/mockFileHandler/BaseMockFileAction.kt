package com.github.akovriginsdg.cpplugin.actions.mockFileHandler

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

open class BaseMockFileAction : AnAction() {

    private val LOG = Logger.getInstance(BaseMockFileAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        LOG.warn("[MockAction] Started for file: ${currentFile.path}")

        // 1. Commit PSI (Обязательно перед работой)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()

        // 2. Определяем Source и Mock в зависимости от того, куда кликнули
        val sourceFile: VirtualFile
        val mockFile: VirtualFile?

        if (currentFile.name.endsWith(".mock.ts")) {
            // Кликнули по моку -> хотим обновить его на основе исходника
            mockFile = currentFile
            // Пытаемся найти исходник: index.mock.ts -> index.ts
            val sourceName = currentFile.name.replace(".mock.ts", ".ts")
            val sibling = currentFile.parent.findChild(sourceName)

            if (sibling == null) {
                Messages.showErrorDialog(project, "Could not find source file '$sourceName' for this mock.", "Error")
                return
            }
            sourceFile = sibling
            LOG.warn("[MockAction] Context: Mock File selected. Source resolved to: ${sourceFile.name}")
        } else {
            // Кликнули по исходнику -> ищем или создаем мок
            sourceFile = currentFile
            val mockName = "${sourceFile.nameWithoutExtension}.mock.ts"
            // Рефреш папки, чтобы увидеть файл, если он только что создан
            sourceFile.parent.refresh(false, false)
            mockFile = sourceFile.parent.findChild(mockName)
            LOG.warn("[MockAction] Context: Source File selected. Mock found: ${mockFile != null}")
        }

        val psiManager = PsiManager.getInstance(project)
        val sourcePsiFile = psiManager.findFile(sourceFile) ?: return

        // 3. Парсим ИСХОДНИК (всегда)
        LOG.warn("[MockAction] Parsing exports from source: ${sourceFile.name}")
        val exports = parsePsiExports(sourcePsiFile)

        if (exports.isEmpty()) {
            LOG.warn("[MockAction] No exports found in ${sourceFile.name}")
            Messages.showInfoMessage(project, "No exported classes or functions found in ${sourceFile.name}.", "Info")
            return
        }

        // 4. Обновляем или Создаем
        if (mockFile != null) {
            val mockPsiFile = psiManager.findFile(mockFile)
            if (mockPsiFile != null) {
                LOG.warn("[MockAction] Updating existing mock...")
                updateMockFile(project, mockPsiFile, exports)
                FileEditorManager.getInstance(project).openFile(mockFile, true)
            }
        } else {
            LOG.warn("[MockAction] Creating new mock...")
            val absoluteImportPath = calculateAbsoluteImportPath(sourceFile) ?: run {
                Messages.showErrorDialog(project, "Could not determine domain path.", "Error")
                return
            }

            val (importsCode, bodyCode) = generateMockContent(exports, absoluteImportPath)
            createMockFile(project, sourceFile.parent, "${sourceFile.nameWithoutExtension}.mock", importsCode, bodyCode)
        }
    }

    private fun parsePsiExports(psiFile: PsiFile): List<ExportedItem> {
        val items = mutableListOf<ExportedItem>()
        val children = psiFile.children

        for (element in children) {
            if (isTypeOrInterface(element)) continue

            // 1. Прямой экспорт: export class Foo
            if (element is JSAttributeListOwner && isExported(element)) {
                if (element is JSClass || element is JSFunction || element is JSVarStatement) {
                    processElement(element, items)
                }
            }

            // 2. Экспорт списка: export { Foo, Bar }
            // Это важно, если исходник написан так: class Foo {}; export { Foo };
            if (element is ES6ExportDeclaration) {
                val specifiers = element.exportSpecifiers
                for (spec in specifiers) {
                    // Разрешаем ссылку (Ctrl+Click logic), чтобы найти, где определен Foo
                    val resolved = spec.reference?.resolve()
                    if (resolved is JSAttributeListOwner) {
                        // Если это класс/функция - обрабатываем
                        processElement(resolved, items)
                    }
                }
            }
        }
        return items
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

    // ... Остальные методы (findClassInFile, hasVariableInFile, processElement, генераторы) без изменений ...
    // Вставьте их сюда из предыдущей версии

    // --- ДЛЯ ПОЛНОТЫ, helper methods ---
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
